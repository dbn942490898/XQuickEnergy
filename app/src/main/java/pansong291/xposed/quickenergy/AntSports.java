package pansong291.xposed.quickenergy;

import org.json.JSONArray;
import org.json.JSONObject;
import pansong291.xposed.quickenergy.hook.AntSportsRpcCall;
import pansong291.xposed.quickenergy.util.Config;
import pansong291.xposed.quickenergy.util.FriendIdMap;
import pansong291.xposed.quickenergy.util.Log;
import pansong291.xposed.quickenergy.util.RandomUtils;
import pansong291.xposed.quickenergy.util.Statistics;

public class AntSports
{
 private static final String TAG = AntSports.class.getCanonicalName();

 public static void start(ClassLoader loader, int times)
 {
  new Thread()
  {
   ClassLoader loader;
   int times;

   public Thread setData(ClassLoader cl, int i)
   {
    loader = cl;
    times = i;
    return this;
   }

   @Override
   public void run()
   {
    try
    {
     if(Config.openTreasureBox())
      queryMyHomePage(loader);
     if(Config.donateCharityCoin())
      queryProjectList(loader);
     if(Config.minExchangeCount() > 0 && Statistics.canExchangeToday() && times == 0)
      queryWalkStep(loader);
    }catch(Throwable t)
    {
     Log.i(TAG, "start.run err:");
     Log.printStackTrace(TAG, t);
    }
   }
  }.setData(loader, times).start();
 }

 private static void queryMyHomePage(ClassLoader loader)
 {
  try
  {
   String s = AntSportsRpcCall.rpcCall_queryMyHomePage(loader);
   JSONObject jo = new JSONObject(s);
   if(jo.getString("resultCode").equals("SUCCESS"))
   {
    s = jo.getString("pathJoinStatus");
    if(s.equals("GOING"))
    {
     FriendIdMap.currentUid = jo.getJSONObject("myPositionModel").getString("userId");
     String rankCacheKey = jo.getString("rankCacheKey");
     JSONArray ja = jo.getJSONArray("treasureBoxModelList");
     for(int i = 0; i < ja.length(); i++)
     {
      parseTreasureBoxModel(loader, ja.getJSONObject(i), rankCacheKey);
     }
     JSONObject joPathRender = jo.getJSONObject("pathRenderModel");
     String title = joPathRender.getString("title");
     int minGoStepCount = joPathRender.getInt("minGoStepCount");
     jo = jo.getJSONObject("dailyStepModel");
     int consumeQuantity = jo.getInt("consumeQuantity");
     int produceQuantity = jo.getInt("produceQuantity");
     String day = jo.getString("day");
     int canMoveStepCount = produceQuantity - consumeQuantity;
     if(canMoveStepCount >= minGoStepCount)
     {
      go(loader, day, rankCacheKey, canMoveStepCount, title);
     }
    }else if(s.equals("NOT_JOIN"))
    {
     JSONArray ja = jo.getJSONArray("allPathBaseInfoList");
     for(int i = ja.length() - 1; i >= 0; i--)
     {
      jo = ja.getJSONObject(i);
      if(jo.getBoolean("unlocked"))
       break;
     }
     String title = jo.getString("title");
     String pathId = jo.getString("pathId");
     join(loader, pathId, title);
    }
   }else
   {
    Log.i(TAG, jo.getString("resultDesc"));
   }
  }catch(Throwable t)
  {
   Log.i(TAG, "queryMyHomePage err:");
   Log.printStackTrace(TAG, t);
  }
 }

 private static void join(ClassLoader loader, String pathId, String title)
 {
  try
  {
   String s = AntSportsRpcCall.rpcCall_join(loader, pathId);
   JSONObject jo = new JSONObject(s);
   if(jo.getString("resultCode").equals("SUCCESS"))
   {
    Log.other("成功加入〈" + title + "〉路线");
    queryMyHomePage(loader);
   }else
   {
    Log.i(TAG, jo.getString("resultDesc"));
   }
  }catch(Throwable t)
  {
   Log.i(TAG, "join err:");
   Log.printStackTrace(TAG, t);
  }
 }

 private static void go(ClassLoader loader, String day, String rankCacheKey, int stepCount, String title)
 {
  try
  {
   String s = AntSportsRpcCall.rpcCall_go(loader, day, rankCacheKey, stepCount);
   JSONObject jo = new JSONObject(s);
   if(jo.getString("resultCode").equals("SUCCESS"))
   {
    Log.other("〈" + title + "〉路线前进了〈" + jo.getInt("goStepCount") + "步〉");
    boolean completed = jo.getString("completeStatus").equals("COMPLETED");
    JSONArray ja = jo.getJSONArray("allTreasureBoxModelList");
    for(int i = 0; i < ja.length(); i++)
    {
     parseTreasureBoxModel(loader, ja.getJSONObject(i), rankCacheKey);
    }
    if(completed)
    {
     Log.other("〈" + title + "〉路线已完成");
     queryMyHomePage(loader);
    }
   }else
   {
    Log.i(TAG, jo.getString("resultDesc"));
   }
  }catch(Throwable t)
  {
   Log.i(TAG, "go err:");
   Log.printStackTrace(TAG, t);
  }
 }

 private static void parseTreasureBoxModel(ClassLoader loader, JSONObject jo, String rankCacheKey)
 {
  try
  {
   String canOpenTime = jo.getString("canOpenTime");
   String issueTime = jo.getString("issueTime");
   String boxNo = jo.getString("boxNo");
   String userId = jo.getString("userId");
   if(canOpenTime.equals(issueTime))
   {
    openTreasureBox(loader, boxNo, userId);
   }else
   {
    long cot = Long.parseLong(canOpenTime);
    long now = Long.parseLong(rankCacheKey);
    long delay = cot - now;
    Log.recordLog("还有 " + delay + "ms 才能开宝箱", "");
    if(delay < Config.checkInterval())
    {
     new Thread()
     {
      long delay;
      ClassLoader loader;
      String boxNo;
      String userId;

      public Thread setData(long l, ClassLoader cl, String bN, String uid)
      {
       delay = l - 1000;
       loader = cl;
       boxNo = bN;
       userId = uid;
       return this;
      }

      @Override
      public void run()
      {
       try
       {
        if(delay > 0) sleep(delay);
        Log.recordLog("蹲点开箱开始", "");
        long startTime = System.currentTimeMillis();
        while(System.currentTimeMillis() - startTime < 5_000)
        {
         if(openTreasureBox(loader, boxNo, userId) > 0)
          break;
         sleep(200);
        }
       }catch(Throwable t)
       {
        Log.i(TAG, "parseTreasureBoxModel.run err:");
        Log.printStackTrace(TAG, t);
       }
      }

     }.setData(delay, loader, boxNo, userId).start();
    }
   }
  }catch(Throwable t)
  {
   Log.i(TAG, "parseTreasureBoxModel err:");
   Log.printStackTrace(TAG, t);
  }
 }

 private static int openTreasureBox(ClassLoader loader, String boxNo, String userId)
 {
  try
  {
   String s = AntSportsRpcCall.rpcCall_openTreasureBox(loader, boxNo, userId);
   JSONObject jo = new JSONObject(s);
   if(jo.getString("resultCode").equals("SUCCESS"))
   {
    JSONArray ja = jo.getJSONArray("treasureBoxAwards");
    int num = 0;
    for(int i = 0; i < ja.length(); i++)
    {
     jo = ja.getJSONObject(i);
     num += jo.getInt("num");
     Log.other("开宝箱获得〈" + num + jo.getString("name") + "〉");
    }
    return num;
   }else
   {
    Log.recordLog(jo.getString("resultDesc"), "");
   }
  }catch(Throwable t)
  {
   Log.i(TAG, "openTreasureBox err:");
   Log.printStackTrace(TAG, t);
  }
  return 0;
 }

 private static boolean queryProjectList(ClassLoader loader)
 {
  boolean haveMore = false;
  try
  {
   String s = AntSportsRpcCall.rpcCall_queryProjectList(loader, 0);
   JSONObject jo = new JSONObject(s);
   if(jo.getString("resultCode").equals("SUCCESS"))
   {
    int charityCoinCount = jo.getInt("charityCoinCount");
    if(charityCoinCount < 10) return false;
    jo = jo.getJSONObject("projectPage");
    haveMore = jo.getBoolean("haveMore");
    JSONArray ja = jo.getJSONArray("data");
    for(int i = 0; i < ja.length(); i++)
    {
     jo = ja.getJSONObject(i).getJSONObject("basicModel");
     if(jo.getString("footballFieldStatus").equals("OPENING_DONATE"))
     {
      donate(loader, charityCoinCount / 10 * 10, jo.getString("projectId"), jo.getString("title"));
      break;
     }
    }
   }else
   {
    Log.recordLog(TAG, jo.getString("resultDesc"));
   }
  }catch(Throwable t)
  {
   Log.i(TAG, "queryProjectList err:");
   Log.printStackTrace(TAG, t);
  }
  return haveMore;
 }

 private static void donate(ClassLoader loader, int donateCharityCoin, String projectId, String title)
 {
  try
  {
   String s = AntSportsRpcCall.rpcCall_donate(loader, donateCharityCoin, projectId);
   JSONObject jo = new JSONObject(s);
   if(jo.getString("resultCode").equals("SUCCESS"))
   {
    Log.other("捐赠〈" + title + "〉〈" + donateCharityCoin + "运动币〉");
   }else
   {
    Log.i(TAG, jo.getString("resultDesc"));
   }
  }catch(Throwable t)
  {
   Log.i(TAG, "donate err:");
   Log.printStackTrace(TAG, t);
  }
 }

 private static void queryWalkStep(ClassLoader loader)
 {
  try
  {
   String s = AntSportsRpcCall.rpcCall_queryWalkStep(loader);
   JSONObject jo = new JSONObject(s);
   if(jo.getString("resultCode").equals("SUCCESS"))
   {
    jo = jo.getJSONObject("dailyStepModel");
    int produceQuantity = jo.getInt("produceQuantity");
    int hour = Integer.parseInt(Log.getFormatTime().split(":")[0]);
    if(produceQuantity > Config.minExchangeCount() || hour >= Config.latestExchangeTime())
    {
     s = AntSportsRpcCall.rpcCall_exchange(loader, produceQuantity, 3);
     jo = new JSONObject(s);
     if(jo.getBoolean("isSuccess"))
     {
      s = AntSportsRpcCall.rpcCall_exchange_success(loader, jo.getString("exchangeId"));
      jo = new JSONObject(s);
      if(jo.getBoolean("isSuccess"))
      {
       int userCount = jo.getInt("userCount");
       double amount = jo.getJSONObject("userAmount").getDouble("amount");
       Log.other("捐出〈" + userCount + "步〉，兑换〈" + amount + "元〉公益金");
       Statistics.exchangeToday();
      }else
      {
       Log.i(TAG, jo.getString("resultDesc"));
      }
     }else if(s.contains("已捐步"))
     {
      Statistics.exchangeToday();
     }else
     {
      Log.i(TAG, jo.getString("resultDesc"));
     }
    }
   }else
   {
    Log.i(TAG, jo.getString("resultDesc"));
   }
  }catch(Throwable t)
  {
   Log.i(TAG, "queryWalkStep err:");
   Log.printStackTrace(TAG, t);
  }
 }
}
