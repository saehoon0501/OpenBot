package org.openbot.voice;

import static android.content.ContentValues.TAG;
import static org.openbot.OpenBotApplication.getContext;

import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class WeatherGetter extends Thread {
    private static String weatherInfo = "";

    @Override
    public void run() {
        try {
          SimpleDateFormat dtf = new SimpleDateFormat("yyyyMMdd");
          //SimpleDateFormat dtf1 = new SimpleDateFormat("HHmm");
          Calendar calendar1 = Calendar.getInstance();

          Date dateObj = calendar1.getTime();
          String currentDate = dtf.format(dateObj);
          System.out.println(currentDate);
          //String currentTime = dtf1.format(dateObj);

          String endPoint = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/";
          String serviceKey = "2an6dDORxvF659L4yfzziSH5WbJgYc8OCVYhtL3C7GwVJ3xxTf9GxCstopQHCIkSbfdUAnK4oJDce4ZH1DJPXQ%3D%3D";
          String pageNo = "1";
          String numOfRows = "10";
          String baseDate = currentDate;    //현재 날짜
          String baseTime = "1100"; //원하는 시간
          String nx = "37";
          String ny = "127";    //위치(위도, 경도)

          String s = endPoint + "getVilageFcst?serviceKey=" + serviceKey
                  + "&pageNo=" + pageNo
                  + "&numOfRows=" + numOfRows
                  + "&dataType=JSON"
                  + "&base_date=" + baseDate
                  + "&base_time=" + baseTime
                  + "&nx=" + nx
                  + "&ny=" + ny;

          URL url = new URL(s);
          URLConnection conn = url.openConnection();
          //conn.setConnectTimeout(8000);
          //conn.setRequestMethod("GET");

          BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//                    if (conn.getResponseCode() == 200) {
//                        bufferedReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//                    } else {
//                        InputStream is = conn.getErrorStream();
//                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                        byte[] byteBuffer = new byte[1024];
//                        byte[] byteData = null;
//                        int nLength = 0;
//                        while ((nLength = is.read(byteBuffer, 0, byteBuffer.length)) != -1) {
//                            baos.write(byteBuffer, 0, nLength);
//                        }
//                        byteData = baos.toByteArray();
//                        String response1 = new String(byteData);
//                        Log.d(TAG, "response = " + response1);
//                    }
          StringBuilder stringBuilder = new StringBuilder();
          String line;
          while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
          }
          bufferedReader.close();
          String result = stringBuilder.toString();
          weatherInfo = result;
          System.out.println(weatherInfo);

        } catch (IOException e) {
          e.printStackTrace();
        } catch (Exception e) {
          e.printStackTrace();
        }
    }
    public static String getWeatherInfo() {
        System.out.println("weatherInfo : " + weatherInfo);

        int currentTemperature = 0;

        int skyState = 0;       // 1:맑음, 3:구름 많음, 4:흐림
        int ptyState = 0;       // 0: 없음, 1:비, 2:비/눈, 3:눈, 4:소나기

        try {
            JSONObject mainObject = new JSONObject(weatherInfo);
            JSONArray itemArray = mainObject.getJSONObject("response").getJSONObject("body").getJSONObject("items").getJSONArray("item");
            for (int i = 0; i < itemArray.length(); i++) {
                JSONObject item = itemArray.getJSONObject(i);
                String category = item.getString("category");
                String value = item.getString("fcstValue");

                System.out.println(category + "  " + value);
                if (category.equals("TMP")) {
                    currentTemperature = Integer.parseInt(value);
                } else if (category.equals("SKY")) {
                    skyState = Integer.parseInt(value);
                } else if (category.equals("PTY")) {
                    ptyState = Integer.parseInt(value);
                }
            }
        } catch (JSONException ex) {
            ex.printStackTrace();
        }

        String result = "";
        result = "오늘 기온은 " + currentTemperature + "도 이고, ";

        if (skyState == 1)
            result += "하늘은 맑은 상태이며 ";
        else if (skyState == 3)
            result += "하늘은 구름이 많은 상태이며, ";
        else
            result += "하늘은 흐린 상태이며, ";

        if (ptyState == 0)
            result += "비 혹은 눈이 오지 않습니다.";
        else if (ptyState == 1)
            result += "비가 내리고 있습니다.";
        else if (ptyState == 2)
            result += "비와 눈이 내리고 있습니다.";
        else if (ptyState == 3)
            result += "눈이 내리고 있습니다.";
        else
            result += "소나기가 내리고 있습니다.";

        System.out.println(result);
        return result;
    }
}
