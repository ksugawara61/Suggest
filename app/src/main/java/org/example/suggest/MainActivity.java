package org.example.suggest;

import android.app.SearchManager;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

public class MainActivity extends AppCompatActivity {

    private EditText origText;
    private ListView suggList;
    private TextView ebandText;

    private Handler guiThread;
    private ExecutorService suggThread;
    private Runnable updateTask;
    private Future<?> suggPending;
    private List<String> items;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initThreading();
        findViews();
        setListeners();
        setAdapters();
    }

    /**
     * ユーザインタフェース要素のハンドルを取得
     */
    private void findViews() {
        origText = (EditText)findViewById(R.id.original_text);
        suggList = (ListView)findViewById(R.id.result_list);
        ebandText = (TextView)findViewById(R.id.eband_text);
    }

    /**
     * リストビューのためにアダプターをセットアップする
     */
    private void setAdapters() {
        items = new ArrayList<String>();
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items);
        suggList.setAdapter(adapter);
    }

    /**
     * ユーザインターフェースハンドラをセットアップ
     */
    private void setListeners() {
        // テキスト変更のためのリスナを定義する
        TextWatcher textWatcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                /* 何もしない */
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                queueUpdate(1000);
            }

            public void afterTextChanged(Editable s) {
                /* 何もしない */
            }
        };

        // オリジナルのテキストフィールドにリスナをセットアップ
        origText.addTextChangedListener(textWatcher);

        // 項目をクリックするためのリスナを定義
        AdapterView.OnItemClickListener clickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String query = (String)parent.getItemAtPosition(position);
                doSearch(query);
            }
        };
    }

    private void doSearch(String query) {
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.putExtra(SearchManager.QUERY, query);
        startActivity(intent);
    }

    private void initThreading() {
        guiThread = new Handler();
        suggThread = Executors.newSingleThreadExecutor();

        // このタスクはヒントのリストを受け取り画面を更新する
        updateTask = new Runnable() {
            public void run() {
                // ヒントのもととなるテキストを受け取る
                String original = origText.getText().toString().trim();

                // 古いヒントがある場合はそれを取り消す
                if (suggPending != null) {
                    suggPending.cancel(true);
                }

                // 操作するテキストがあることをチェックする
                if (original.length() != 0) {
                    // プログラムが操作をしていることをユーザに知らせる
                    setText(R.string.working);
                }

                // ヒントの表示を始めるが終わりを持たない
                try {
                    SuggestTask suggestTask = new SuggestTask(
                            MainActivity.this,
                            original
                    );
                    suggPending = suggThread.submit(suggestTask);
                } catch (RejectedExecutionException e) {
                    // 新しいタスクを起動できない
                    setText(R.string.error);
                }

            }
        };
    }

    /**
     * 短いディレイのあとで更新リクエストを発行する
     */
    private void queueUpdate(long delayMillis) {
        // まだ前の更新リクエストが実行されていなければ、それを取り消す
        guiThread.removeCallbacks(updateTask);
        // 引数の時間が経過しても何もなｋれば更新を開始する
        guiThread.postDelayed(updateTask, delayMillis);
    }

    /**
     * 画面上のリストを書き換える（他のスレッドから呼び出される）
     */
    public void setSuggestions(List<String> suggestions) {
        guiSetList(suggList, suggestions);
    }

    /**
     * GUIの変更は、すべてGUIスレッドで行わなければならない
     */
    private void guiSetList(final ListView view, final List<String> list) {
        guiThread.post(new Runnable() {
           public void run() {
               setList(list);
           }
        });
    }

    /**
     * メッセージを表示する
     */
    private void setText(int id) {
        adapter.clear();
        adapter.add(getResources().getString(id));
    }

    /**
     * リストを表示する
     */
    private void setList(List<String> list) {
        adapter.clear();
        adapter.addAll(list);
    }
}
