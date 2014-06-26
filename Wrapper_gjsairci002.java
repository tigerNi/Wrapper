import com.google.common.collect.Lists;
        import com.qunar.qfwrapper.bean.booking.BookingInfo;
        import com.qunar.qfwrapper.bean.booking.BookingResult;
        import com.qunar.qfwrapper.bean.search.*;
        import com.qunar.qfwrapper.constants.Constants;
        import com.qunar.qfwrapper.developer.QFGetMethod;
        import com.qunar.qfwrapper.interfaces.QunarCrawler;
        import com.qunar.qfwrapper.util.QFHttpClient;
        import com.qunar.qfwrapper.util.QFPostMethod;
        import org.apache.commons.httpclient.Header;
        import org.apache.commons.httpclient.HttpStatus;
        import org.apache.commons.httpclient.cookie.CookiePolicy;
        import org.apache.commons.httpclient.util.URIUtil;
        import org.apache.commons.lang.StringUtils;
        import org.slf4j.Logger;
        import org.slf4j.LoggerFactory;

        import java.text.ParseException;
        import java.text.SimpleDateFormat;
        import java.util.*;

/**
 * Created by peng.ni on 14-6-16.
 */


public class Wrapper_gjsairci002 implements QunarCrawler {

    private static final Logger logger = LoggerFactory.getLogger("CrawlerLog");
    private static final String CODEBASE = "gjsairci002";
    private static final String url="https://caleb.china-airlines.com/olbn/Travel.aspx";


    @Override
    public String getHtml(FlightSearchParam flightSearchParam) {

        QFHttpClient httpClient = new QFHttpClient(flightSearchParam, false);
        QFPostMethod post = new QFPostMethod(url);
        QFGetMethod get = null;
        try {
            String forQuery = String.format("lang=zh-CN&depstn=%s&arrstn=%s&cls=Y&adult_no=1&child_no=0&Dep_Date=%s&Ret_Date=%s&Trip=RT&url=FARE&BackNotSure=N&auto=Y", flightSearchParam.getDep(), flightSearchParam.getArr(), flightSearchParam.getDepDate(), flightSearchParam.getRetDate());
            post.setQueryString(URIUtil.encodeQuery(forQuery));
            httpClient.getParams().setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
            post.setRequestHeader("cookie", "mainIndexURL=http%3A//www.china-airlines.com/cn/index.htm");
            httpClient.executeMethod(post);
            if (post.getStatusCode() == HttpStatus.SC_MOVED_PERMANENTLY ||
                    post.getStatusCode() == HttpStatus.SC_MOVED_TEMPORARILY) {
                // 从头中取出转向的地址
                Header locationHeader = post.getResponseHeader("location");
                if (locationHeader != null) {
                    String location = locationHeader.getValue();
                    String tmpCookies = "";
                    Header[] headers = post.getResponseHeaders("Set-Cookie");
                    for (Header header : headers) {
                        tmpCookies += header.getValue();
                    }
                    get = new QFGetMethod("https://caleb.china-airlines.com" + location);
                    get.setRequestHeader("cookie", tmpCookies);
                    httpClient.executeMethod(get);
                    return get.getResponseBodyAsString();
                } else {
                    logger.info("Location field value is null.");
                }
            }

        } catch (Exception e) {
            logger.error("获取分页的html异常", e);
            return "Exception";
        } finally {
            post.releaseConnection();
            if (get != null) {
                get.releaseConnection();
            }

        }
        return "Exception";
    }

    private String getResultWithoutSignal(String rawData) {
        StringBuilder dataBuilder = new StringBuilder();
        String[] seg = rawData.split(",");
        for (String aSeg : seg) {
            dataBuilder.append(aSeg);
        }
        return dataBuilder.toString();
    }


    @Override
    public ProcessResultInfo process(String html, FlightSearchParam flightSearchParam) {
        ProcessResultInfo processResultInfo = new ProcessResultInfo();
        List<RoundTripFlightInfo> data = Lists.newArrayList();
        if ("Exception".equals(html)) {
            processResultInfo.setStatus(Constants.CONNECTION_FAIL);
            logger.warn("Exception {},{},{}", CODEBASE, Constants.CONNECTION_FAIL, flightSearchParam.toString());
            processResultInfo.setData(data);
            processResultInfo.setRet(false);
            return processResultInfo;
        }

        String noticeContent = StringUtils.substringBetween(html, "<div id=\"NoticeContent\">", "</div>");//要是为：：QU00103  Departure flight full. Please select a different cabin or flight. Thanks! 则为无航班 查找为空
        if (StringUtils.contains(noticeContent, "QU00103  Departure flight full. Please select a different cabin or flight. Thanks!")) {
            processResultInfo.setStatus(Constants.INVALID_AIRLINE);
            logger.warn("{},{},{}", CODEBASE, Constants.INVALID_AIRLINE, flightSearchParam.toString());
            processResultInfo.setData(data);
            processResultInfo.setRet(false);
            return processResultInfo;

        }

        // 提取最低价格
        String minPriceTable = StringUtils.substringBetween(html, "<table class=\"tableStyle01 priceTotalTable\" cellspacing=\"0\" rules=\"all\" id=\"priceTotalTable\" ", "</table>");
        String minPriceRaw = StringUtils.substringBetween(minPriceTable, "<td>", "<input ");//最小价格
        minPriceRaw = StringUtils.deleteWhitespace(minPriceRaw);
        String minPriceString = minPriceRaw;
        if (minPriceRaw.contains(",")) {
            minPriceString = getResultWithoutSignal(minPriceRaw);
        }
        String minPriceTaxRaw = StringUtils.substringBetween(minPriceTable, "</td><td>", "<input ");//税费
        minPriceTaxRaw = StringUtils.deleteWhitespace(minPriceTaxRaw);
        String minPriceTaxString = minPriceTaxRaw;
        if (minPriceTaxRaw.contains(",")) {
            minPriceTaxString = getResultWithoutSignal(minPriceTaxRaw);
        }

        String minPriceUnitRaw = StringUtils.substringBetween(minPriceTable, "<th scope=\"col\">", "</th>");//货币单位
        String unit = StringUtils.substringBetween(minPriceUnitRaw, "(", ")");

        String[] yearRaw = StringUtils.substringsBetween(html, "<span class=\"descTrip\">", "</div>");
        String depYear = StringUtils.substringBetween(yearRaw[0], "：", "/");
        String arrYear = StringUtils.substringBetween(yearRaw[1], "：", "/");

        //--------此处修改 双程航班
        String[] flightTable = StringUtils.substringsBetween(html, "<table class=\"tableStyle01 priceTotalTable\" cellspacing=\"0\" rules=\"all\" id=\"gvFlight\"", "</table>");//航班具体信息


        String[] flightListsDep = StringUtils.substringsBetween(flightTable[0], "<tr>", "</tr>"); //存放去程航班的列表
        String[] flightListsArr = StringUtils.substringsBetween(flightTable[1], "<tr>", "</tr>"); //存放去程航班的列表

        List<String> usefulFlightListsDep = Lists.newArrayList();   //存放可以购买的航班的列表
        List<String> usefulFlightListsArr = Lists.newArrayList();   //存放可以购买的航班的列表

        for (String flightList : flightListsDep) {
            if (StringUtils.contains(flightList, "radio")) {
                usefulFlightListsDep.add(flightList);
            }
        }

        for (String flightList : flightListsArr) {
            if (StringUtils.contains(flightList, "radio")) {
                usefulFlightListsArr.add(flightList);
            }
        }

        if (usefulFlightListsDep.size() == 0||usefulFlightListsArr.size()==0) {
            processResultInfo.setStatus(Constants.NO_RESULT);
            logger.warn("{},{},{}", CODEBASE, Constants.NO_RESULT, flightSearchParam.toString());
            processResultInfo.setData(data);
            processResultInfo.setRet(true);
            return processResultInfo;
        } else {
            for(String dep:usefulFlightListsDep)
            {
                for(String arr:usefulFlightListsArr)
                {
                    data.add(bulidRoundTrip(dep, arr, flightSearchParam, minPriceString, minPriceTaxString, unit, depYear, arrYear));
                }

            }
            logger.info("dep:{} arr:{}  depDate:{}  arrDate:{}",
                    flightSearchParam.getDep(), flightSearchParam.getArr(), flightSearchParam.getDepDate(),
                    flightSearchParam.getRetDate());

            processResultInfo.setData(data);
            processResultInfo.setRet(true);
            processResultInfo.setStatus(Constants.SUCCESS);
            return processResultInfo;
        }


    }


    private RoundTripFlightInfo bulidRoundTrip(String dep,String arr,FlightSearchParam flightSearchParam, String minPriceString, String minPriceTaxString, String unit, String depYear,String arrYear)
    {
        RoundTripFlightInfo roundTripFlightInfo=new RoundTripFlightInfo();
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd");
        Date retDate=null;
        try {
            retDate=sdf.parse(flightSearchParam.getRetDate());
        } catch (ParseException e) {
            logger.error("Exception:parse retDate error", e);
        }
        roundTripFlightInfo.setRetdepdate(retDate);

        String[] flightDetailArr = StringUtils.substringsBetween(arr, "<td>", "</td>");//截取回程航班具体信息
        String[] flightDetailDep = StringUtils.substringsBetween(dep, "<td>", "</td>");//截取去程航班具体信息
        List<String> retFlightNo=Lists.newArrayList();
        List<FlightSegement> retInfoArr=Lists.newArrayList();
        List<FlightSegement> retInfoDep=Lists.newArrayList();

        retInfoArr.add(getFlightSegement(flightDetailArr, arrYear));
        roundTripFlightInfo.setRetinfo(retInfoArr);
        retInfoDep.add(getFlightSegement(flightDetailDep,depYear));
        roundTripFlightInfo.setInfo(retInfoDep);

        retFlightNo.add(flightDetailArr[5]);
        roundTripFlightInfo.setRetflightno(retFlightNo);

        roundTripFlightInfo.setDetail(getFlightDetail(minPriceString,minPriceTaxString,unit,flightDetailDep,flightSearchParam)); 
        return roundTripFlightInfo;

    }

    private FlightSegement getFlightSegement(String[] flightDetail,String year)
    {
        FlightSegement flightSegement=new FlightSegement();
        flightSegement.setArrDate(year+"-"+flightDetail[2].split("/")[1].split(" ")[0]);
        flightSegement.setDepDate(year+"-"+flightDetail[2].split("/")[0].split(" ")[0]);
        flightSegement.setFlightno(flightDetail[5]);
        String company=flightDetail[5].substring(0,2);
        flightSegement.setCompany(company);
        flightSegement.setAircraft(flightDetail[4]);
        flightSegement.setArrairport(flightDetail[1].split(" ")[1]);
        flightSegement.setDepairport(flightDetail[1].split(" ")[0]);
        flightSegement.setArrtime(flightDetail[2].split("/")[1].split(" ")[1]);
        flightSegement.setDeptime(flightDetail[2].split("/")[0].split(" ")[1]);
        return flightSegement;
    }

    private FlightDetail getFlightDetail(String minPriceString, String minPriceTaxString,String unit,String[] flightDetail,FlightSearchParam flightSearchParam)
    {
        FlightDetail detail=new FlightDetail();
        detail.setWrapperid(CODEBASE);
        List<String> flightNo=Lists.newArrayList();
        flightNo.add(flightDetail[5]);
        detail.setFlightno(flightNo);
        detail.setPrice(Double.parseDouble(minPriceString));
        detail.setTax(Double.parseDouble(minPriceTaxString));
        detail.setMonetaryunit(unit);
        detail.setArrcity(flightDetail[1].split(" ")[1]);
        detail.setDepcity(flightDetail[1].split(" ")[0]);
        Date date=null;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        try {
            date = sdf.parse(flightSearchParam.getDepDate());
        } catch (ParseException e) {
            logger.error("Exception:parse depDate error",e);
        }
        detail.setDepdate(date);
        return detail;
    }

    @Override
    public BookingResult getBookingInfo(FlightSearchParam flightSearchParam) {

        BookingResult bookingResult = new BookingResult();
        BookingInfo bookingInfo = new BookingInfo();
        bookingInfo.setMethod("post");
        bookingInfo.setAction("https://caleb.china-airlines.com/olbn/Travel.aspx");

        Map<String, String> input = new HashMap<String, String>();
        input.put("lang", "zh-CN");
        input.put("depstn", flightSearchParam.getDep());
        input.put("arrstn", flightSearchParam.getArr());
        input.put("cls", "Y");
        input.put("adult_no", "1");
        input.put("child_no", "0");
        input.put("Dep_Date", flightSearchParam.getDepDate());
        input.put("Ret_Date", flightSearchParam.getRetDate());
        input.put("Trip", "RT");
        input.put("BackNotSure", "N");
        input.put("auto", "Y");
        bookingResult.setRet(true);
        bookingInfo.setInputs(input);
        bookingResult.setData(bookingInfo);
        //如果不行的话 换用下面的代码
        //String forQuery=String.format("lang=zh-CN&depstn=%s&arrstn=%s&cls=Y&adult_no=1&child_no=0&Dep_Date=%s&Ret_Date=%s&Trip=OW&url=FARE&BackNotSure=N&auto=Y",flightSearchParam.getDep(),flightSearchParam.getArr(),flightSearchParam.getDepDate(),flightSearchParam.getRetDate());
        //bookingInfo.setAction("https://caleb.china-airlines.com/olbn/Travel.aspx?"+forQuery);

        return bookingResult;
    }

    public static void main(String[] args) {

        FlightSearchParam f = new FlightSearchParam();
        f.setArr("TPE");
        f.setDep("PEK");
        f.setDepDate("2014-06-26");
        f.setRetDate("2014-06-28");
        Wrapper_gjsairci002 wr = new Wrapper_gjsairci002();
        String sw = wr.getHtml(f);
      //  System.out.println(sw);
        ProcessResultInfo pri = wr.process(sw, f);
    }


}

