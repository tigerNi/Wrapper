import com.google.common.collect.Lists;
import com.qunar.qfwrapper.bean.booking.BookingInfo;
import com.qunar.qfwrapper.bean.booking.BookingResult;
import com.qunar.qfwrapper.bean.search.*;
import com.qunar.qfwrapper.constants.Constants;
import com.qunar.qfwrapper.developer.QFGetMethod;
import com.qunar.qfwrapper.developer.QFPostMethod;
import com.qunar.qfwrapper.interfaces.QunarCrawler;
import com.qunar.qfwrapper.util.QFHttpClient;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by peng.ni on 14-6-19.
 */

public class Wrapper_gjdweb00029 implements QunarCrawler {
    private static final Logger logger = LoggerFactory.getLogger("CrawlerLog");
    private static final String CODEBASE = "gjdweb00029";
    private static final String url="http://www.onetravel.com/default.aspx";

    @Override
    public String getHtml(FlightSearchParam param) {

        QFHttpClient qfHttpClient=new QFHttpClient(param,false);
        QFPostMethod post=new QFPostMethod(url);
        QFPostMethod post2=null;
        QFGetMethod get=null;
        QFGetMethod get2=null;
        QFGetMethod get3=null;


        try{

            //第一次请求 post  请求航班参数 获得SESSION信息
            String forQuery = String.format("tabid=3582&fromDt=%s&fromTm=1100&toDt=null&toTm=1100&rt=false&ad=1&se=0&ch=0&class=1&infl=0&infs=0&airpref=&preftyp=1&searchflxdt=false&IsNS=false&searchflxarpt=false&from=%s&to=%s",
                    param.getDepDate(),param.getDep(),param.getArr());
            post.setQueryString(URIUtil.encodeQuery(forQuery));
            qfHttpClient.executeMethod(post);
            //第二次请求 get  进行第一次跳转 获得cookie信息
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
                    get = new QFGetMethod("http://www.onetravel.com" + location);

                    get.setFollowRedirects(false);
                    get.setRequestHeader("cookie", tmpCookies);
                    qfHttpClient.executeMethod(get);

                    //第三次请求 get2 进行第二次跳转 获得cookies信息 并进入搜索页
                    location=get.getResponseHeader("location").getValue();
                    get2=new QFGetMethod("http://www.onetravel.com" + location);
                    Header[] headers3 = get.getResponseHeaders("Set-Cookie");
                    for (Header header : headers3) {
                        tmpCookies += header.getValue();
                    }
                    get2.setRequestHeader("cookie", tmpCookies);
                    qfHttpClient.executeMethod(get2);

                    //第四次请求 post2 进行搜索结果页面
                    Header[] headers2 = get2.getResponseHeaders("Set-Cookie");
                    for (Header headert : headers2) {
                        tmpCookies += headert.getValue();
                    }
                    tmpCookies="fpPageLimit=100;"+tmpCookies;
                    Thread.sleep(8000);
                    String forQuery2 = String.format("http://www.onetravel.com/Default.aspx?tabid=1919&sid=1&oa=%s&da=%s&adt=1&chd=0&snr=0&infl=0&infs=0&dd=%s&tt=ONEWAYTRIP",param.getDep(),param.getArr(),param.getDepDate());
                    post2= new QFPostMethod(forQuery2);
                    post2.setRequestHeader("cookie", tmpCookies);
                    qfHttpClient.executeMethod(post2);

                    //第五次请求  get3 进入搜索结果页面 将信息扩展为每页100条信息
                    get3=new QFGetMethod(forQuery2);
                    get3.setRequestHeader("cookie", tmpCookies);
                    qfHttpClient.executeMethod(get3);
                    return get3.getResponseBodyAsString();

                }
            }
        }catch (Exception e) {
            logger.error("获取分页的html异常", e);
        } finally {
            post.releaseConnection();
            if(post2!=null)
            {
                post2.releaseConnection();
            }
            if(get!=null)
            {
                get.releaseConnection();
            }
            if(get2!=null)
            {
                get2.releaseConnection();
            }
            if(get3!=null)
            {
                get3.releaseConnection();
            }

        }
        return "Exception";
    }

    @Override
    public ProcessResultInfo process(String html, FlightSearchParam param) {
        ProcessResultInfo processResultInfo = new ProcessResultInfo();
        List<OneWayFlightInfo> data = Lists.newArrayList();
        if ("Exception".equals(html)) {
            processResultInfo.setStatus(Constants.CONNECTION_FAIL);
            logger.warn("Exception {},{},{}", CODEBASE, Constants.CONNECTION_FAIL, param.toString());
            processResultInfo.setData(data);
            processResultInfo.setRet(false);
            return processResultInfo;
        }

        String resultRaw=StringUtils.substringBetween(html,"ctl10_ctl04_ctl00_NoFilteredContractFoundPnlSlider","href");
        if (resultRaw!=null)
        {
            String result=StringUtils.substringBetween(resultRaw,">","<");
            result=StringUtils.deleteWhitespace(result);
            if(StringUtils.contains(result,"Noresultsmatchyourselections"))
            {
                processResultInfo.setStatus(Constants.NO_RESULT);
                logger.warn("{},{},{}", CODEBASE, Constants.NO_RESULT, param.toString());
                processResultInfo.setData(data);
                processResultInfo.setRet(true);
                return processResultInfo;
            }
            else
            {
                processResultInfo.setStatus(Constants.INVALID_AIRLINE);
                logger.warn("{},{},{}", CODEBASE, Constants.INVALID_AIRLINE, param.toString());
                processResultInfo.setData(data);
                processResultInfo.setRet(false);
                return processResultInfo;
            }

        }

        resultRaw=StringUtils.substringBetween(html,"<title>","</title>");
        if(resultRaw!=null && StringUtils.contains(resultRaw,"We have encountered some system errors"))
        {
            processResultInfo.setStatus(Constants.INVALID_AIRLINE);
            logger.warn("{},{},{}", CODEBASE, Constants.INVALID_AIRLINE, param.toString());
            processResultInfo.setData(data);
            processResultInfo.setRet(false);
            return processResultInfo;
        }


        String[] flightRaw=splitInfo(html);

        if(flightRaw==null)
        {
            processResultInfo.setStatus(Constants.INVALID_AIRLINE);
            logger.warn("{},{},{}", CODEBASE, Constants.INVALID_AIRLINE, param.toString());
            processResultInfo.setData(data);
            processResultInfo.setRet(false);
            return processResultInfo;
        }

        for(String tmp:flightRaw)
        {
            data.add(getDetailInfo(tmp, param));
        }
        processResultInfo.setData(data);
        processResultInfo.setStatus(Constants.SUCCESS);
        return processResultInfo;
    }

    public OneWayFlightInfo getDetailInfo(String data,FlightSearchParam param) //提取具体航班字段
    {
        OneWayFlightInfo oneWayFlightInfo=new OneWayFlightInfo();
        String year=param.getDepDate().split("-")[0];
        String month=param.getDepDate().split("-")[1];

        //大致分割网页信息
        String[] airLineLocationRaw=StringUtils.substringsBetween(data,"<td width=\"600\" class=\"apf\"","</td>");
        String[] airLineTimeRaw=StringUtils.substringsBetween(data,"<td width=\"510\" class=\"apf\"","</td>");
        String[] airLineFlightRaw=StringUtils.substringsBetween(data,"<td class=\"pL5 padbot12\"","</td>");
        String[] airLineBaseMoneyRaw=StringUtils.substringsBetween(data,"<span id='BaseFareCOA","</span>");
        String[] airLineTaxMoneyRaw=StringUtils.substringsBetween(data,"<span id='taxesSup","</span>");
        String[] airLineCompanyRaw=StringUtils.substringsBetween(data,"src='/I","='absmiddle'>");

        //提取具体信息
        List<FlightSegement> info=Lists.newArrayList();
        for(int i=0;i<airLineLocationRaw.length;i++)
        {
            FlightSegement flightSegement=new FlightSegement();
            //提取位置信息 并存储
            String[] airLineLocation=StringUtils.substringsBetween(airLineLocationRaw[i],"<span class=\"apf\">(",")");
            flightSegement.setDepairport(airLineLocation[0]);
            flightSegement.setArrairport(airLineLocation[1]);
            //提取航班/飞机信息
            String company=StringUtils.substringBetween(airLineCompanyRaw[i],"/AI/",".gif");
            company=StringUtils.deleteWhitespace(company);
            String flight=StringUtils.substringBetween(airLineFlightRaw[i],"<b>","</b>");
            flightSegement.setAircraft(flight);
            String flightNo=StringUtils.substringBetween(airLineFlightRaw[i],"<span class=\"apf gray\">","</span>");
            flightNo=StringUtils.deleteWhitespace(flightNo);
            flightSegement.setFlightno(company+flightNo.split("&nbsp;")[1]);//调试检查是否正确
            flightSegement.setCompany(company);//由于setCompany需要flightNo比对 所以要在后面赋值
            //提取时间
            String[] airLineTime=StringUtils.substringsBetween(airLineTimeRaw[i], "<strong>", "</strong>");
            String[] airLineDate=StringUtils.substringsBetween(airLineTimeRaw[i],"<span class='apf '>","</span>");
            if(!("error".equals(timeTo24Patton(airLineTime[0]))))
                flightSegement.setDeptime(timeTo24Patton(airLineTime[0]));
            if(!("error".equals(timeTo24Patton(airLineTime[1]))))
                flightSegement.setArrtime(timeTo24Patton(airLineTime[1]));
            String depDate=dateToPatton(airLineDate[0]);
            String arrDate=dateToPatton(airLineDate[1]);
            flightSegement.setDepDate(whetherNextYear(depDate,month,year));
            flightSegement.setArrDate(whetherNextYear(arrDate, month, year));
            info.add(flightSegement);
        }

        String baseMoney=StringUtils.substringBetween(airLineBaseMoneyRaw[0],"title=\"","\">");
        String taxMoney=StringUtils.substringBetween(airLineTaxMoneyRaw[0],"title=\"","\">");
        FlightDetail flightDetail=getFlightDetailInfo(param,baseMoney,taxMoney,info);
        oneWayFlightInfo.setDetail(flightDetail);
        oneWayFlightInfo.setInfo(info);
        return oneWayFlightInfo;
    }

    public String timeTo24Patton(String data) //时间格式转换
    {
        data=StringUtils.deleteWhitespace(data);
        if(data.contains("am"))
        {
            return data.split("am")[0];
        }
        if(data.contains("pm"))
        {
            String timeRaw=data.split("pm")[0];
            String []timePart=timeRaw.split(":");
            Integer timePartNum;
            if(!timePart[0].equals("12"))
            {
                timePartNum=Integer.parseInt(timePart[0])+12;
                timePart[0]=timePartNum.toString();
            }
            return  timePart[0]+":"+timePart[1];
        }
        return "error";
    }

    public String dateToPatton(String data) //日期格式转换
    {
        data=StringUtils.deleteWhitespace(data);
        String dd=data.substring(0,2);
        String mm=data.substring(2);
        String date="JanFebMarAprMayJunJulAugSepOctNovDec";
        int mmInt=date.indexOf(mm);
        return (mmInt/3+1)+"-"+dd;
    }

    public String whetherNextYear(String date,String month,String year)//判断年份是否为下一年
    {
        int newYear=Integer.parseInt(year);
        if(Integer.parseInt(date.split("-")[0])<Integer.parseInt(month))
        {
            return (newYear+1)+"-"+date;
        }
        else
            return  newYear+"-"+date;
    }

    public FlightDetail getFlightDetailInfo(FlightSearchParam param,String baseMoney,String taxMoney,List<FlightSegement> info)
    {
        List<String> flightNo=Lists.newArrayList();
        for(FlightSegement fs:info)
        {
            flightNo.add(fs.getFlightno());
        }
        FlightDetail flightDetail=new FlightDetail();
        flightDetail.setWrapperid(CODEBASE);
        flightDetail.setDepcity(param.getDep());
        flightDetail.setArrcity(param.getArr());
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd");
        Date date=null;
        try {
            date=sdf.parse(param.getDepDate());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        flightDetail.setDepdate(date);
        flightDetail.setMonetaryunit("USD");
        flightDetail.setPrice(Double.parseDouble(baseMoney));
        flightDetail.setTax(Double.parseDouble(taxMoney));
        flightDetail.setFlightno(flightNo);
        return  flightDetail;

    }

    public String[] splitInfo(String  html){ //分割航班和航空公司
        String[] airLineRaw=StringUtils.substringsBetween(html," <div class=\"oneselectflistbrd\"","<div class=\"blk_nrmal\"");
        return airLineRaw;
    }


    @Override
    public BookingResult getBookingInfo(FlightSearchParam param) {
        BookingResult bookingResult=new BookingResult();
        BookingInfo bookingInfo=new BookingInfo();
        bookingInfo.setMethod("post");
        bookingInfo.setAction("http://www.onetravel.com/Default.aspx");
        Map<String,String> input = new HashMap<String, String>();
        input.put("tabid", "1919");
        input.put("sid","1");
        input.put("oa",param.getDep());
        input.put("da",param.getArr());
        input.put("adt","1");
        input.put("chd","0");
        input.put("snr","0");
        input.put("infl","0");
        input.put("infs","0");
        input.put("dd",param.getDepDate());
        input.put("tt","ONEWAYTRIP");
        bookingInfo.setInputs(input);
        bookingResult.setData(bookingInfo);
        bookingResult.setRet(true);
        return bookingResult;
    }

    public static void main(String[] args)
    {
        FlightSearchParam f = new FlightSearchParam();
        f.setArr("PEK");
        f.setDep("HKG");
        f.setDepDate("2014-06-26");
        f.setRetDate("2014-06-26");
        Wrapper_gjdweb00029 wr = new Wrapper_gjdweb00029();
        String sw = wr.getHtml(f);
        ProcessResultInfo pri=wr.process(sw,f);
        //wr.splitInfo(sw);
        //System.out.println(sw);
    }
}
