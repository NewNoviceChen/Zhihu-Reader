package com.github.newnovicechen.zhihureader.model;

public class Question {
    public Question(String id, String title) {
        this.id = id;
        this.title = title;
    }

    String id;
    String title;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
