package com.github.newnovicechen.zhihureader.model;

public class Answer {
    String authorName;
    String answerContent;

    public Answer(String authorName, String answerContent) {
        this.authorName = authorName;
        this.answerContent = answerContent;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAnswerContent() {
        return answerContent;
    }

    public void setAnswerContent(String answerContent) {
        this.answerContent = answerContent;
    }
}
