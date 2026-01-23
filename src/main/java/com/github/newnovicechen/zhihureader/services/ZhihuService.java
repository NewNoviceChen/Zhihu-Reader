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

import java.util.*;

@Service(Service.Level.PROJECT)
public final class ZhihuService {
    private static final String BASE_URL_V3 = "https://www.zhihu.com/api/v3";
    private static final String BASE_URL_V4 = "https://www.zhihu.com/api/v4";

    private static @NotNull Map<String, String> getStringStringMap() {
        Map<String, String> headMap = new HashMap<>();
        headMap.put("Cookie", "_xsrf=ZKUUOHRdaWFRGGR0fB4RwcpGuexKgKNn; _zap=39255a4e-6545-430f-87e1-6facf98daef0; d_c0=UOSTbFMTNxqPTmX_iwxlV1LLE_5mB8AS0xc=|1743152652; __snaker__id=ax5Pe4mDlE8KsQBz; q_c1=fbda936a23664d33b5cdaaa27de07e9c|1750390088000|1750390088000; edu_user_uuid=edu-v1|d173d463-7557-414a-8c04-29299142a427; z_c0=2|1:0|10:1767833700|4:z_c0|92:Mi4xN0MzY1RBQUFBQUJRNUpOc1V4TTNHaVlBQUFCZ0FsVk4td3RMYWdEYlFTQUJRVFJMaS05OXRsZ2VJd0QtRktuZGtR|8948e53e88055159aa0d473591372a9d05f4e73ea7c0f9daed6ea3d3cd5063af; Hm_lvt_98beee57fd2ef70ccdd5ca52b9740c49=1768367179,1768368744,1768376298,1768465292; __zse_ck=005_75zdkxxBuFRaMZoHaJt1Hm40884oeGwK2M82pLn8hp6qQMCzQ5g6LyMO4w2RK8/qEL4GLIURVknq06zIvmhcaZpSd9rT=LpUTA=IyhwvjOPecQFnxw3eTjQljndiLDoc-KNPny+NA3nHFzaq7uzAor6aKFRi4yAWIRkPfYVJzDiEl8nglp/hLc2cI7h1MWPxumlJ8ur0sUMMbtw20iuG+d2aog0tfkmzPTxenVXm09upUm7OQHuxq/HoPce+2Qk3A; BEC=f7bc18b707cd87fca0d61511d015686f");
        return headMap;
    }

    public List<Question> zhihuRecommend() {
        List<Question> questionList = new ArrayList<>();
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL_V3 + "/feed/topstory/recommend")).newBuilder().build();
        Map<String, String> headMap = getStringStringMap();
        JsonObject jsonObject = SendHttpUtil.sendHttpGet(url, headMap);
        JsonArray datas = jsonObject.getAsJsonArray("data");
        for (JsonElement data : datas) {
            String title = data.getAsJsonObject().getAsJsonObject("target").getAsJsonObject().getAsJsonObject("question").get("title").getAsString();
            String id = data.getAsJsonObject().getAsJsonObject("target").getAsJsonObject().getAsJsonObject("question").get("id").getAsString();
            questionList.add(new Question(id, title));
        }
        return questionList;
    }

    public List<Answer> zhihuAnswer(String questionId) {
        List<Answer> answerList = new ArrayList<>();
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_URL_V4 + "/questions/" + questionId + "/answers")).newBuilder().build();
        Map<String, String> headMap = getStringStringMap();
        JsonObject jsonObject = SendHttpUtil.sendHttpGet(url, headMap);
        JsonArray datas = jsonObject.getAsJsonArray("data");
        for (JsonElement data : datas) {
            HttpUrl answerUrl = Objects.requireNonNull(HttpUrl.parse(data.getAsJsonObject().get("url").getAsString()))
                    .newBuilder()
                    .addQueryParameter("include", "data[*].is_normal,admin_closed_comment,reward_info,is_collapsed,annotation_action,annotation_detail,collapse_reason,is_sticky,collapsed_by,suggest_edit,comment_count,can_comment,content,editable_content,voteup_count,reshipment_settings,comment_permission,mark_infos,created_time,updated_time,review_info,question.detail,answer_count,follower_count,excerpt,detail,question_type,title,id,created,updated_time,relevant_info,excerpt,label_info,relationship.is_authorized,is_author,voting,is_thanked,is_nothelp,is_labeled,is_recognized")
                    .build();
            JsonObject answerDetail = SendHttpUtil.sendHttpGet(answerUrl, getStringStringMap());
            String answerContent = answerDetail.get("content").getAsString();
            String authorName = answerDetail.get("author").getAsJsonObject().get("name").getAsString();
            Answer answer = new Answer(authorName, answerContent);
            answerList.add(answer);
        }
        return answerList;
    }
}