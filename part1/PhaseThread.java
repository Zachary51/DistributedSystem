package part1;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequestWithBody;


import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import part2.SharedData;

public class PhaseThread extends Thread implements Runnable{
  private int startSkierId;
  private int endSkierId;
  private int startTime;
  private int endTime;
  private int runTimes;
  private AtomicInteger numSuccessRequest;
  private AtomicInteger numFailedRequest;
  private CountDownLatch currentCountdownLatch;
  private CountDownLatch nextPhaseCountdownLatch;
  private BlockingQueue<SharedData> sharedRecords;
  public static final Logger logger = LogManager.getLogger(PhaseThread.class.getName());


  public PhaseThread(int startSkierId, int endSkierId,
      int startTime, int endTime, int runTimes, CountDownLatch currentLatch, CountDownLatch nextLatch,
      BlockingQueue<SharedData> sharedRecords, AtomicInteger numSuccessRequest, AtomicInteger numFailedRequest){
    this.startSkierId = startSkierId;
    this.endSkierId = endSkierId;
    this.startTime = startTime;
    this.endTime = endTime;
    this.runTimes = runTimes;
    this.currentCountdownLatch = currentLatch;
    this.nextPhaseCountdownLatch = nextLatch;
    this.sharedRecords = sharedRecords;
    this.numSuccessRequest = numSuccessRequest;
    this.numFailedRequest = numFailedRequest;
  }

  @Override
  public void run() {
    try{
      currentCountdownLatch.await();  // Wait until part of threads in the previous phase finish their jobs
      for(int i = 0; i < runTimes; i++){
        String targetUrl = constructUrl();
        JSONObject messageBody = buildMessageBody();
        long start = System.currentTimeMillis();
        int code = sendPostRequest(targetUrl, messageBody);
        long latency = System.currentTimeMillis() - start;
        this.sharedRecords.add(new SharedData(start, latency, code));
      }
    } catch (InterruptedException e){
      logger.info(e.getMessage());
    } finally {
      if(this.nextPhaseCountdownLatch != null){
        this.nextPhaseCountdownLatch.countDown();
      }
    }
  }

  private String constructUrl(){
    int resortId = ThreadLocalRandom.current().nextInt(0, 11);
    int seasonId = ThreadLocalRandom.current().nextInt(2016, 2020);
    int dayId = ThreadLocalRandom.current().nextInt(1, 366);
    int randomSkierId = ThreadLocalRandom.current().nextInt(startSkierId, endSkierId + 1);

    String baseUrl = Constants.LOCAL_ENV ? Constants.LOCAL_URL : Constants.REMOTE_URL;
    String targetUrl = baseUrl + "/skiers/" + resortId +
                      "/seasons/" + seasonId +
                      "/days/" + dayId +
                      "/skiers/" + randomSkierId;
    logger.info(targetUrl);
    return targetUrl;
  }

  private JSONObject buildMessageBody(){
    JSONObject object = new JSONObject();
    int time = ThreadLocalRandom.current().nextInt(startTime, endTime + 1);
    int liftId = ThreadLocalRandom.current().nextInt(0, Constants.numLifts + 1);
    object.put("time", time);
    object.put("liftID", liftId);
    logger.debug(object.toString());
    return object;
  }

  private int sendPostRequest(String urlPath, JSONObject body){
    HttpRequestWithBody httpRequestWithBody = Unirest.post(urlPath)
        .header("Accept", "application/json")
        .header("Content-Type", "application/json;charset=UTF-8");
    httpRequestWithBody.body(body.toString());
    try{
      HttpResponse e = httpRequestWithBody.asString();
      logger.debug("Response Code: " + e.getStatus());
      int code = e.getStatus();
      if(code == 200){
//        SharedHttpMetric.successfulRequestsCount.incrementAndGet();
        this.numSuccessRequest.getAndIncrement();
      } else {
//        SharedHttpMetric.failedRequestsCount.incrementAndGet();
        this.numFailedRequest.getAndIncrement();
        if(code == 404){
          logger.error("Resource not found");
        } else if(code == 500){
          logger.error("Internal server error");
        }
      }
      return code;
    } catch (UnirestException e){
      logger.error(e.getMessage());
    }
    return 500;
  }
}
