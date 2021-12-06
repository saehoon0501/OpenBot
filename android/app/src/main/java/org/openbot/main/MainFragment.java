package org.openbot.main;

import static android.content.ContentValues.TAG;
import static android.util.Log.ERROR;
import static android.widget.Toast.makeText;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openbot.R;
import org.openbot.common.FeatureList;
import org.openbot.databinding.FragmentMainBinding;
import org.openbot.model.Category;
import org.openbot.model.SubCategory;
import org.openbot.original.DefaultActivity;
import org.openbot.original.PlayActivity;
import org.openbot.voice.TimeAlarmManager;
import org.openbot.voice.WeatherGetter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;
import timber.log.Timber;

public class MainFragment extends Fragment implements OnItemClickListener<SubCategory>, RecognitionListener {

  public static PlayActivity playActivity;

  // voice recognizer listener
  public interface VoiceListener {
    void onReceivedEvent();
    void onRight();
    void onLeft();
    void onStraight();
  }

  private VoiceListener voiceListener;


  private MainViewModel mViewModel;
  private FragmentMainBinding binding;
  private CategoryAdapter adapter;

  private SetupTask voiceThread;
  public static TextToSpeech textToSpeech = null;

  @Nullable
  @Override
  public View onCreateView(
          @NonNull LayoutInflater inflater,
          @Nullable ViewGroup container,
          @Nullable Bundle savedInstanceState) {
    binding = FragmentMainBinding.inflate(inflater, container, false);

    textToSpeech = new TextToSpeech(getContext(), new TextToSpeech.OnInitListener() {
      @Override
      public void onInit(int status) {
        if(status != ERROR) {
          // 언어를 선택한다.
          textToSpeech.setLanguage(Locale.KOREAN);
        }
      }
    });

    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
    binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
    adapter = new CategoryAdapter(FeatureList.getCategories(), this);
    binding.list.setAdapter(adapter);
  }

  @Override
  public void onItemClick(SubCategory subCategory) {

    Timber.d("onItemClick: %s", subCategory.getTitle());

    switch (subCategory.getTitle()) {
      case FeatureList.DEFAULT:
        Intent intent = new Intent(requireActivity(), DefaultActivity.class);
        startActivity(intent);
        break;

      case FeatureList.FREE_ROAM:
        Navigation.findNavController(requireView())
                .navigate(R.id.action_mainFragment_to_robotCommunicationFragment);
        break;

      case FeatureList.DATA_COLLECTION:
        Navigation.findNavController(requireView())
                .navigate(R.id.action_mainFragment_to_loggerFragment);
        break;

      case FeatureList.CONTROLLER:
        // For a library module, uncomment the following line
        // intent = new Intent(this, ControllerActivity.class);
        // startActivity(intent);
        break;
      case FeatureList.AUTOPILOT:
        Navigation.findNavController(requireView())
                .navigate(R.id.action_mainFragment_to_autopilotFragment);
        break;

      case FeatureList.OBJECT_NAV:
        Navigation.findNavController(requireView())
                .navigate(R.id.action_mainFragment_to_objectNavFragment);
        break;

      case FeatureList.CONTROLLER_MAPPING:
        Navigation.findNavController(requireView())
                .navigate(R.id.action_mainFragment_to_controllerMappingFragment);
        break;

      case FeatureList.MODEL_MANAGEMENT:
        Navigation.findNavController(requireView())
                .navigate(R.id.action_mainFragment_to_modelManagementFragment);
        break;

      case FeatureList.PLAY:
        Intent intent2 = new Intent(requireActivity(), PlayActivity.class);
        startActivity(intent2);
        break;

      case FeatureList.VOICE:
        ArrayList<Category> categories = FeatureList.getCategories();
        initPermission();
        if(!canVoiceRec) {
          categories.get(0).getSubCategories().get(1).setBackgroundColor("#01DF3A");
          adapter = new CategoryAdapter(categories, this);
          binding.list.setAdapter(adapter);
//          Toast.makeText(getContext(), "활성화", Toast.LENGTH_SHORT).show();
          voiceThread = new SetupTask(this);

          if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(getContext(), new TextToSpeech.OnInitListener() {
              @Override
              public void onInit(int status) {
                if(status != ERROR) {
                  // 언어를 선택한다.
                  textToSpeech.setLanguage(Locale.KOREAN);
                }
              }
            });
          }
          voiceThread.execute();
        }
        else {
          categories.get(0).getSubCategories().get(2).setBackgroundColor("#FF4000");
          adapter = new CategoryAdapter(categories, this);
          binding.list.setAdapter(adapter);

//          synchronized (this) {
//            if (textToSpeech != null) {
//              textToSpeech.stop();
//              textToSpeech.shutdown();
//              textToSpeech = null;
//            }
//          }

//          Toast.makeText(getContext(), "비활성화", Toast.LENGTH_SHORT).show();
          canVoiceRec = false;
          try {
            if(voiceThread != null && voiceThread.getStatus() == AsyncTask.Status.RUNNING) {
              voiceThread.cancel(true);
            }
          } catch (Exception ignored) {}
          recognizer.cancel();
          recognizer.shutdown();
        }
        break;
    }
  }

  public static Boolean canVoiceRec = false;

  private static SpeechRecognizer recognizer; // 앱이 종료되지 않는 이상 종료되지 않게 유지
  private TimeAlarmManager timeAlarmManager = new TimeAlarmManager();
  private WeatherGetter weatherGetter = new WeatherGetter();

  private static final String FAIL = "fail";
  private static final String SUCCESS = "success";
  private static final String KEYPHRASE = "max"; // 야 맥스

  /* Used to handle permission request */
  private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

  private static String command;

  public void initPermission() {
    // Check if user has given permission to record audio
    int permissionCheck = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO);
    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.INTERNET, Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
    }
  }
  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task

        new SetupTask(this).execute();
      } /*else {
        finish();
      }*/
    }
  }

  // 이게 앱 종료되는 것인지 잘 모르겠음. 스레드도 같이 종료되나??
  @Override
  public void onDestroyView() {
    super.onDestroyView();

//    if (recognizer != null) {
//      recognizer.cancel();
//      recognizer.shutdown();
//    }

  }

  @Override
  public void onBeginningOfSpeech() {

  }

  @Override
  public void onEndOfSpeech() {
    if (!recognizer.getSearchName().equals(FAIL))
      switchSearch(FAIL);
  }

  @Override
  public void onPartialResult(Hypothesis hypothesis) {
    if (hypothesis == null)
      return;

    String text = hypothesis.getHypstr();
    if (text.equals(KEYPHRASE))
      switchSearch(SUCCESS);
//        else
//            ((TextView) findViewById(R.id.ListeningTextView)).setText(text);
  }

  private void switchSearch(String searchName) {
    recognizer.stop();

    // If we are not spotting, start listening with timeout (10000 ms or 10 seconds).
    if (searchName.equals(FAIL)) {
      recognizer.startListening(searchName);
    } else {
      //Toast.makeText(getContext(), "success", Toast.LENGTH_SHORT).show();

      //synchronized (this) {
      textToSpeech.speak("네 말씀하세요", TextToSpeech.QUEUE_FLUSH, null);
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ex) {
        ex.printStackTrace();
      }
      //}
      Handler handler = new Handler();
      handler.postDelayed(new Runnable() {
        public void run() {
//        알림음 나오게 해야될 듯
          Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
          intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getActivity().getPackageName());
          intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");

          android.speech.SpeechRecognizer speechRecognizer;
          speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(getActivity());
          speechRecognizer.setRecognitionListener(listener);
          speechRecognizer.startListening(intent);
        }
      }, 500);
    }
  }

  @Override
  public void onResult(Hypothesis hypothesis) {
//    ((TextView) findViewById(R.id.ListeningTextView)).setText("");
//    if (hypothesis != null) {
//      String text = hypothesis.getHypstr();
//      makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
//    }
  }

  @Override
  public void onError(Exception e) {
    Toast.makeText(getContext(), "오류 발생", Toast.LENGTH_SHORT).show();
//    ((TextView) findViewById(R.id.flagTextView)).setText("오류 발생");
  }

  @Override
  public void onTimeout() {
    switchSearch(FAIL);
  }

//  public void init() {
//    new SetupTask((MainActivity) getActivity()).execute();
//
//  }

  private static class SetupTask extends AsyncTask<Void, Void, Exception> {
    WeakReference<MainFragment> activityReference;

    SetupTask(MainFragment fragment) {
      this.activityReference = new WeakReference<>(fragment);
    }

    @Override
    protected void onPreExecute() {
      canVoiceRec = true;

      super.onPreExecute();
    }

    @Override
    protected Exception doInBackground(Void... params) {
      if(!canVoiceRec || isCancelled()) return null;
      try {
        Assets assets = new Assets(activityReference.get().getActivity());
        File assetDir = assets.syncAssets();
        activityReference.get().setupRecognizer(assetDir);
      } catch (IOException e) {
        return e;
      }
      if(!canVoiceRec || isCancelled()) return null;
      return null;
    }

    @Override
    protected void onPostExecute(Exception result) {
      if (result == null) {
        activityReference.get().switchSearch(FAIL);
      }
    }
  }

  private void setupRecognizer(File assetsDir) throws IOException {
    // The recognizer can be configured to perform multiple searches
    // of different kind and switch between them

    recognizer = SpeechRecognizerSetup.defaultSetup()
            .setAcousticModel(new File(assetsDir, "en-us-ptm"))
            .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))

            .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)

            .getRecognizer();
    recognizer.addListener(this);

        /* In your application you might not need to add all those searches.
          They are added here for demonstration. You can leave just one.
         */

    // Create keyword-activation search.
    recognizer.addKeyphraseSearch(FAIL, KEYPHRASE);
    recognizer.addKeyphraseSearch(SUCCESS, KEYPHRASE);
  }

  boolean singleResult = true;

  private android.speech.RecognitionListener listener = new android.speech.RecognitionListener() {
    @Override
    public void onReadyForSpeech(Bundle params) {
      Log.i("voice", "start");
//            ((TextView) findViewById(R.id.ListeningTextView)).setText("Listening...");
      Toast.makeText(getContext(), "음성인식을 시작합니다.", Toast.LENGTH_SHORT).show();
//      //synchronized (this) {
//      textToSpeech.speak("네 말씀하세요", TextToSpeech.QUEUE_FLUSH, null);
//      //}
//      try {
//        Thread.sleep(1500);
//      } catch (InterruptedException ex) {
//        ex.printStackTrace();
//      }
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float rmsdB) {
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
      Log.i("voice", "end");

      singleResult = true;
      Toast.makeText(getContext(), "음성인식을 종료합니다.", Toast.LENGTH_SHORT).show();

      Handler handler = new Handler();
      handler.postDelayed(new Runnable() {
        @Override
        public void run() {
          recognizer.startListening(FAIL);
        }
      }, 2000);

    }

    @Override
    public void onError(int error) {
      String message;
      switch (error) {
        case android.speech.SpeechRecognizer.ERROR_AUDIO:
          message = "오디오 에러";
          break;
        case android.speech.SpeechRecognizer.ERROR_CLIENT:
          message = "클라이언트 에러";
          break;
        case android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
          message = "퍼미션 없음";
          break;
        case android.speech.SpeechRecognizer.ERROR_NETWORK:
          message = "네트워크 에러";
          break;
        case android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
          message = "네트워크 타임아웃";
          break;
        case android.speech.SpeechRecognizer.ERROR_NO_MATCH:
          message = "찾을 수 없음";
          break;
        case android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
          message = "RECOGNIZER가 바쁨";
          break;
        case android.speech.SpeechRecognizer.ERROR_SERVER:
          message = "서버가 이상함";
          break;
        case android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
          message = "말하는 시간초과";
          break;
        default:
          message = "알 수 없는 오류임";
          break;
      }
      Toast.makeText(getContext(), "에러가 발생하였습니다. : " + message, Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onResults(Bundle results) {
      if (singleResult) {
        ArrayList<String> matches = results.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);

        if (matches != null && matches.size() > 0) {
          command = matches.get(0);
          doCommand(command);
          Log.i("voice", "command : " + matches.get(0));
        }
        singleResult=false;
      }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }
  };

  private void doCommand(String command) {
    if (command != null) {
      String str = command;

      Toast.makeText(getContext(), str, Toast.LENGTH_SHORT).show();

      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
      Calendar calendar = Calendar.getInstance();

      int alarmByHour = 0;
      int alarmByMin = 0;

      if (str.contains("시간 뒤 알림") || str.contains("시간 뒤에 알림")) {  //시간 알림
        alarmByHour = str.charAt(0) - '0';
        //int hour = calendar.get(Calendar.HOUR);

        calendar.add(Calendar.HOUR, alarmByHour);
        calendar.set(Calendar.SECOND, 3);
        calendar.set(Calendar.MILLISECOND, 0);

        String reservationTime = dateFormat.format(calendar.getTime());
        Toast.makeText(getContext(), reservationTime + "에 알림 예약되었습니다", Toast.LENGTH_SHORT).show();

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy년 MM월 dd일 HH시 mm분");
        simpleDateFormat.format(calendar.getTime());
        textToSpeech.speak(simpleDateFormat.format(calendar.getTime()) + "에 알림 예약되었습니다.", TextToSpeech.QUEUE_FLUSH, null);
        try {
          timeAlarmManager.reservationTimeByHour(calendar.getTime(), getContext());
        } catch (NullPointerException ex) {
          Toast.makeText(getContext(), "몇 시간 후인지 정확히 인식되지 않았습니다.", Toast.LENGTH_SHORT).show();
        }

      } else if (str.contains("분 뒤 알림") || str.contains("분 뒤에 알림")) { // 분 알림
        alarmByMin = str.charAt(0) - '0';

        calendar.add(Calendar.MINUTE, alarmByMin);
        calendar.set(Calendar.SECOND, 3);
        calendar.set(Calendar.MILLISECOND, 0);

        String reservationTime = dateFormat.format(calendar.getTime());
        Toast.makeText(getContext(), reservationTime + "에 알림 예약되었습니다", Toast.LENGTH_SHORT).show();

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy년 MM월 dd일 HH시 mm분");

        textToSpeech.speak(simpleDateFormat.format(calendar.getTime()) + "에 알림 예약되었습니다.", TextToSpeech.QUEUE_FLUSH, null);
        try {
          timeAlarmManager.reservationTimeByMin(calendar.getTime(), getContext());
          System.out.println(reservationTime + "에 알림 설정되었다");
        } catch (NullPointerException ex) {
          if (getContext() == null) {
            Toast.makeText(getContext(), "context null error", Toast.LENGTH_SHORT).show();
          } else {
            Toast.makeText(getContext(), "몇 분 후인지 정확히 인식되지 않았습니다.", Toast.LENGTH_SHORT).show();
            //     textToSpeech.speak("몇 분 후인지 정확히 인식되지 않았습니다.", TextToSpeech.QUEUE_FLUSH, null);
          }
        }
      } else if (str.contains("기분 좋아") || str.contains("행복")) {         //사용자의 기분에 맞춰서 voice 이미지 변경

        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CategoryAdapter(FeatureList.changeVoiceCategoryImage("smile"), this);
        binding.list.setAdapter(adapter);
        textToSpeech.speak("저도 행복해요", TextToSpeech.QUEUE_FLUSH, null);

      } else if (str.contains("피곤") || str.contains("슬퍼") || str.contains("우울")) {

        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CategoryAdapter(FeatureList.changeVoiceCategoryImage("sad"), this);
        binding.list.setAdapter(adapter);
        textToSpeech.speak("힘내세요", TextToSpeech.QUEUE_FLUSH, null);
      } else if (str.contains("날씨") || str.contains("날씨 어때") || str.contains("날 씨")) {

        try {
          Thread weatherGetting = new WeatherGetter();
          weatherGetting.start();
          weatherGetting.join();

          String weatherInfo = WeatherGetter.getWeatherInfo();
          Toast.makeText(getContext(), weatherInfo, Toast.LENGTH_SHORT).show();
          textToSpeech.speak(weatherInfo, TextToSpeech.QUEUE_FLUSH, null);
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }

      } else if (str.contains("자율 주행") || str.contains("자율주행") || str.contains("자유 주행")) {

        if (playActivity != null) {
          voiceListener = (VoiceListener) playActivity;
          voiceListener.onReceivedEvent();
        } else {
          Intent intent = new Intent(requireActivity(), PlayActivity.class);
          intent.putExtra("start request", 1000);
          startActivity(intent);
        }

      } else if (str.contains("왼쪽")) {
        if (playActivity != null) {
          voiceListener = (VoiceListener) playActivity;
          voiceListener.onLeft();
        } else {
          Intent intent = new Intent(requireActivity(), PlayActivity.class);
          intent.putExtra("start request", 2000);
          startActivity(intent);
        }

      } else if (str.contains("오른쪽")) {
        if (playActivity != null) {
          voiceListener = (VoiceListener) playActivity;
          voiceListener.onRight();
        } else {
          Intent intent = new Intent(requireActivity(), PlayActivity.class);
          intent.putExtra("start request", 3000);
          startActivity(intent);
        }
      } else if (str.contains("직진")) {
        if (playActivity != null) {
          voiceListener = (VoiceListener) playActivity;
          voiceListener.onStraight();
        } else {
          Intent intent = new Intent(requireActivity(), PlayActivity.class);
          intent.putExtra("start request", 4000);
          startActivity(intent);
        }
      } else {
        Toast.makeText(getContext(), "null", Toast.LENGTH_SHORT).show();
      }
    }

  }
}