package com.github.newnovicechen.zhihureader.services;

import com.github.newnovicechen.zhihureader.model.Answer;
import com.github.newnovicechen.zhihureader.model.Question;
import com.github.newnovicechen.zhihureader.utils.SendHttpUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.components.Service;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Service(Service.Level.PROJECT)
public final class ZhihuService {
    private static final String BASE_URL_V3 = "https://www.zhihu.com/api/v3";
    private static final String BASE_URL_V4 = "https://www.zhihu.com/api/v4";
    private @Nullable String userCookie;

    public void setUserCookie(@Nullable String cookie) {
        this.userCookie = cookie;
    }

    // 获取当前Cookie的方法
    public @Nullable String getUserCookie() {
        return userCookie;
    }

    public boolean isCookieSetAndValid() {
        // 简单的检查，可以根据需要增加更复杂的校验逻辑，例如检查是否包含特定键
        return userCookie != null && !userCookie.trim().isEmpty();
    }

    private @NotNull Map<String, String> getAuthHeadMap() {
        Map<String, String> headMap = new HashMap<>();
        if (userCookie != null) {
            headMap.put("Cookie", userCookie);
        }
        return headMap;
    }

    public List<Question> zhihuRecommend() {
        if (!isCookieSetAndValid()) {
            throw new IllegalStateException("知乎Cookie未设置或无效，请先设置Cookie。");
        }
        List<Question> questionList = new ArrayList<>();
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL_V3 + "/feed/topstory/recommend")).newBuilder().build();
        Map<String, String> headMap = getAuthHeadMap();
        JsonObject jsonObject = SendHttpUtil.sendHttpGet(url, headMap);
        JsonArray datas = jsonObject.getAsJsonArray("data");
        for (JsonElement data : datas) {
            String title = data.getAsJsonObject().getAsJsonObject("target").getAsJsonObject().getAsJsonObject("question").get("title").getAsString();
            String id = data.getAsJsonObject().getAsJsonObject("target").getAsJsonObject().getAsJsonObject("question").get("id").getAsString();
            questionList.add(new Question(id, title));
        }
        return questionList;
    }

    public List<Answer> zhihuAnswer(String questionId, int offset) {
        if (!isCookieSetAndValid()) {
            throw new IllegalStateException("知乎Cookie未设置或无效，请先设置Cookie。");
        }
        List<Answer> answerList = new ArrayList<>();
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL_V4 + "/questions/" + questionId + "/answers?limit=10&offset=" + offset)).newBuilder().build();
        Map<String, String> headMap = getAuthHeadMap();
        JsonObject jsonObject = SendHttpUtil.sendHttpGet(url, headMap);
        JsonArray datas = jsonObject.getAsJsonArray("data");
        for (JsonElement data : datas) {
            HttpUrl answerUrl = Objects.requireNonNull(HttpUrl.parse(data.getAsJsonObject().get("url").getAsString()))
                    .newBuilder()
                    .addQueryParameter("include", "data[*].is_normal,admin_closed_comment,reward_info,is_collapsed,annotation_action,annotation_detail,collapse_reason,is_sticky,collapsed_by,suggest_edit,comment_count,can_comment,content,editable_content,voteup_count,reshipment_settings,comment_permission,mark_infos,created_time,updated_time,review_info,question.detail,answer_count,follower_count,excerpt,detail,question_type,title,id,created,updated_time,relevant_info,excerpt,label_info,relationship.is_authorized,is_author,voting,is_thanked,is_nothelp,is_labeled,is_recognized")
                    .build();
            JsonObject answerDetail = SendHttpUtil.sendHttpGet(answerUrl, getAuthHeadMap());
            String answerContent = answerDetail.get("content").getAsString();
            String authorName = answerDetail.get("author").getAsJsonObject().get("name").getAsString();
            Answer answer = new Answer(authorName, answerContent);
            answerList.add(answer);
        }
        return answerList;
    }
}