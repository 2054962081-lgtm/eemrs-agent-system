package com.liu.eemrsagent.eval;

public class EvalKeyPoint {
    private String key;
    private String description;

    public EvalKeyPoint() {
    }

    public EvalKeyPoint(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
