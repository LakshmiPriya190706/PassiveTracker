package com.example.passivetracker;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> chatMessages;
    private EditText etMessage;
    private ProgressBar loading;
    private final OkHttpClient client = new OkHttpClient();

    // OpenRouter API configuration
    private static final String OPENROUTER_API_KEY = "Your API key";
    private static final String MODEL_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL_ID = "openai/gpt-3.5-turbo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        etMessage = findViewById(R.id.etMessage);
        loading = findViewById(R.id.chatLoading);
        ImageButton btnSend = findViewById(R.id.btnSend);

        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);

        addMessage("Hello! I'm your wellness assistant. How are you feeling today?", ChatMessage.TYPE_AI);

        btnSend.setOnClickListener(v -> {
            String msg = etMessage.getText().toString().trim();
            if (!msg.isEmpty()) {
                sendMessage(msg);
            }
        });
    }

    private void addMessage(String text, int type) {
        chatMessages.add(new ChatMessage(text, type));
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
    }

    private void sendMessage(String message) {
        addMessage(message, ChatMessage.TYPE_USER);
        etMessage.setText("");

        loading.setVisibility(View.VISIBLE);

        String systemPrompt = "You are a mental wellness support assistant. Rules:\n" +
                "1. Be empathetic and supportive.\n" +
                "2. Do NOT diagnose or give medical advice.\n" +
                "3. Classify user state into EXACTLY one: lonely, irritated, happy, sad, stressed.\n" +
                "4. Respond ONLY with a valid JSON object. Do not include <think> tags or internal reasoning in the final JSON.\n" +
                "\n" +
                "Output format:\n" +
                "{\"llm_state\": \"state\", \"response\": \"empathetic message\"}";

        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("model", MODEL_ID);

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", message);
        messages.add(userMsg);

        jsonBody.add("messages", messages);
        jsonBody.addProperty("temperature", 0.6);
        jsonBody.addProperty("stream", false);

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(MODEL_URL)
                .addHeader("Authorization", "Bearer " + OPENROUTER_API_KEY)
                .addHeader("HTTP-Referer", "https://passivetracker.example.com")
                .addHeader("X-Title", "PassiveTracker")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    Toast.makeText(ChatActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody responseBody = response.body();
                String result = responseBody != null ? responseBody.string() : "";

                if (response.isSuccessful()) {
                    runOnUiThread(() -> parseAIResponse(result));
                } else {
                    runOnUiThread(() -> {
                        loading.setVisibility(View.GONE);
                        Log.e("ChatAPI", "Error " + response.code() + ": " + result);
                        Toast.makeText(ChatActivity.this, "API Error: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void parseAIResponse(String rawJson) {
        try {
            JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();

            JsonObject message = root.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message");

            String content = message.get("content").getAsString();

            int jsonStart = content.indexOf("{");
            int jsonEnd = content.lastIndexOf("}");

            if (jsonStart != -1 && jsonEnd != -1) {
                String jsonStr = content.substring(jsonStart, jsonEnd + 1);
                JsonObject data = JsonParser.parseString(jsonStr).getAsJsonObject();

                String state = data.has("llm_state") ? data.get("llm_state").getAsString() : "NA";
                String response = data.has("response") ? data.get("response").getAsString() : "I hear you.";

                addMessage(response, ChatMessage.TYPE_AI);

                SharedPreferences prefs = getSharedPreferences("WellnessPrefs", MODE_PRIVATE);
                prefs.edit().putString("llm_state", state.toLowerCase().trim()).apply();
            } else {
                addMessage(content.trim(), ChatMessage.TYPE_AI);
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "Parse error", e);
            addMessage("I understand. Tell me more about that.", ChatMessage.TYPE_AI);
        } finally {
            loading.setVisibility(View.GONE);
        }
    }
}
