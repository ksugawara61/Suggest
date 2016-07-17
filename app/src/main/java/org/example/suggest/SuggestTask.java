package org.example.suggest;

import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by katsuya on 16/07/17.
 */
public class SuggestTask implements Runnable {
    private static final String TAG = "SuggestTask";
    private final MainActivity suggest;
    private final String original;

    /**
     * コンストラクタ
     * @param context
     * @param original
     */
    SuggestTask(MainActivity context, String original) {
        this.suggest = context;
        this.original = original;
    }

    public void run() {
        // 入力に対応するヒントを取得する
        List<String> suggestions = doSuggest(original);
        suggest.setSuggestions(suggestions);
    }

    /**
     * Google Suggest APIを呼び出して部分文字列からヒントのリストを作成
     * @param original
     * @return
     */
    private List<String> doSuggest(String original) {
        List<String> messages = new LinkedList<String>();
        String error = null;
        HttpURLConnection con = null;
        Log.d(TAG, "dosuggest(" + original + ")");

        try {
            // タスクが割り込まれているかどうかをチェック
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            // Google APIのためのRESTfulクエリを組み立てる
            String q = URLEncoder.encode(original, "UTF-8");
            URL url = new URL(
                    "http://google.com/complete/search?output=toolbar&q="
                    + q);

            /*URL url = new URL(
                    "http://ff.search.yahoo.com/gossip?output=xml&command="
                    + q);*/

            con = (HttpURLConnection)url.openConnection();
            con.setReadTimeout(1000);
            con.setConnectTimeout(1500);
            con.setRequestMethod("GET");
            con.addRequestProperty("Referer",
                    "http://www.pragprog.com/book/eband4");
            con.setDoInput(true);

            // クエリを開始する
            con.connect();

            // タスクが割り込まれているかどうかチェック
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            // クエリの結果を読む
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(con.getInputStream(), null);
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                if (eventType == XmlPullParser.START_TAG
                        && name.equalsIgnoreCase("suggestion")) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        if (parser.getAttributeName(i).equalsIgnoreCase("data")) {
                            messages.add(parser.getAttributeValue(i));
                        }
                    }
                }
                eventType = parser.next();
            }

            // タスクが割り込まれているかどうかチェック
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
            error = suggest.getResources().getString(R.string.error)
                    + " " + e.toString();
        } catch (XmlPullParserException e) {
            Log.e(TAG, "XmlPullParserException", e);
            error = suggest.getResources().getString(R.string.error)
                    + " " + e.toString();
        } catch (InterruptedException e) {
            Log.e(TAG, "InterruptedException", e);
            error = suggest.getResources().getString(R.string.error)
                    + " " + e.toString();
        } catch (Exception e) {
            Log.e(TAG, "Exception", e);
            error = suggest.getResources().getString(R.string.error)
                    + " " + e.toString();
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }

        // エラーがある場合は、エラー自体を返す
        if (error != null) {
            messages.clear();
            messages.add(error);
        }

        // 何も返されなかった場合
        if (messages.size() == 0) {
            messages.add(suggest.getResources().getString(
                    R.string.no_results
            ));
        }

        // 完了
        Log.d(TAG, "    -> returned " + messages);
        return messages;
    }
}
