
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
 * Created by peng.ni on 14-6-7.
 */

public class Wrapper_gjdairci002 implements QunarCrawler {

    private static final Logger logger = LoggerFactory.getLogger("CrawlerLog");
    private static final String CODEBASE = "gjdairci002";
    private static final String url="https://caleb.china-airlines.com/olbn/Travel.aspx";


    @Override
    public String getHtml(FlightSearchParam flightSearchParam) {

        QFHttpClient httpClient = new QFHttpClient(flightSearchParam, false);
        QFPostMethod post = new QFPostMethod(url);
        QFGetMethod get = null;
        try {
            String forQuery = String.format("lang=zh-CN&depstn=%s&arrstn=%s&cls=Y&adult_no=1&child_no=0&Dep_Date=%s&Ret_Date=%s&Trip=OW&url=FARE&BackNotSure=N&auto=Y", flightSearchParam.getDep(), flightSearchParam.getArr(), flightSearchParam.getDepDate(), flightSearchParam.getRetDate());
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
        List<OneWayFlightInfo> data = Lists.newArrayList();
        if ("Exception".equals(html)) {
            processResultInfo.setStatus(Constants.CONNECTION_FAIL);
            logger.warn("Exception {},{},{}", CODEBASE, Constants.CONNECTION_FAIL, flightSearchParam.toString());
            processResultInfo.setData(data);
            processResultInfo.setRet(false);
            return processResultInfo;
        }

        String noticeContent = StringUtils.substringBetween(html, "<div id=\"NoticeContent\">", "</div>");//要是为：：QU00103  Departure flight full. Please select a different cabin or flight. Thanks! 则为无航班 查找为空
        if (StringUtils.contains(noticeContent, "QU00103  Departure flight full. Please select a different cabin or flight. Thanks!")) {
            processResultInfo.setStatus(Constants.NO_RESULT);
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

        String depYearRaw = StringUtils.substringBetween(html, "<span class=\"descTrip\">", "</div>");
        String depYear = StringUtils.substringBetween(depYearRaw, "：", "/");

        String flightTable = StringUtils.substringBetween(html, "<table class=\"tableStyle01 priceTotalTable\" cellspacing=\"0\" rules=\"all\" id=\"gvFlight\"", "</table>");//航班具体信息
        String[] flightLists = StringUtils.substringsBetween(flightTable, "<tr>", "</tr>"); //存放航班的列表

        List<String> usefulFlightLists = Lists.newArrayList();   //存放可以购买的航班的列表

        for (String flightList : flightLists) {
            if (StringUtils.contains(flightList, "radio")) {
                usefulFlightLists.add(flightList);
            }
        }

        if (usefulFlightLists.size() == 0) {
            processResultInfo.setStatus(Constants.NO_RESULT);
            logger.warn("{},{},{}", CODEBASE, Constants.NO_RESULT, flightSearchParam.toString());
            processResultInfo.setData(data);
            processResultInfo.setRet(true);
            return processResultInfo;
        } else {
            for (String tmp : usefulFlightLists) {
                OneWayFlightInfo ow = getOneWayFlightInfo(flightSearchParam, minPriceString, minPriceTaxString, unit,
                        depYear, tmp);
                data.add(ow);
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

    private OneWayFlightInfo getOneWayFlightInfo(FlightSearchParam flightSearchParam, String minPriceString, String minPriceTaxS, String unit, String depYear, String tmp) {
        OneWayFlightInfo ow = new OneWayFlightInfo();
        List<FlightSegement> fsl = Lists.newArrayList();
        FlightSegement fs = new FlightSegement();
        FlightDetail fd = new FlightDetail();

        String[] flightDetail = StringUtils.substringsBetween(tmp, "<td>", "</td>");
        fs.setFlightno(flightDetail[5]);
        String company=flightDetail[5].substring(0,2);
        fs.setCompany(company);
        fs.setDepairport(flightDetail[1].split(" ")[0]);
        fs.setArrairport(flightDetail[1].split(" ")[1]);
        String[] time = flightDetail[2].split("/");
        fs.setDeptime(time[0].split(" ")[1]);
        fs.setArrtime(time[1].split(" ")[1]);
        fs.setAircraft(flightDetail[4]);

        String depData = time[0].split(" ")[0];
        String arrData = time[1].split(" ")[0];

//此处填写出发日期
        fs.setDepDate(depYear + "-" + depData);
        fs.setArrDate(depYear + "-" + arrData);  //此处填写要填入解析的字段

        fd.setDepcity(flightDetail[1].split(" ")[0]);
        fd.setArrcity(flightDetail[1].split(" ")[1]);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        Date date;
        try {
            date = sdf.parse(flightSearchParam.getDepDate());
            fd.setDepdate(date);
        } catch (ParseException e) {
            //e.printStackTrace();
            logger.error("Exception:parse depDate error",e);
        }
        List<String> flightNoLists = Lists.newArrayList();
        flightNoLists.add(flightDetail[5]);
        fd.setFlightno(flightNoLists);
        fd.setMonetaryunit(unit);
        fd.setTax(Double.parseDouble(minPriceTaxS));
        fd.setPrice(Double.parseDouble(minPriceString));//此处金额为基本价 不含税
        fd.setWrapperid("gjdairci002");
        fsl.add(fs);
        ow.setDetail(fd);
        ow.setInfo(fsl);
        return ow;
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
        input.put("Trip", "OW");
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
        f.setDep("HKG");
        f.setDepDate("2014-06-22");
        f.setRetDate("2014-06-22");
        Wrapper_gjdairci002 wr = new Wrapper_gjdairci002();
        String sw = wr.getHtml(f);
        ProcessResultInfo pri = wr.process(sw, f);
    }


}
