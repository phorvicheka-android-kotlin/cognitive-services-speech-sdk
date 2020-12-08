package com.microsoft.cognitiveservices.speech.samples.speechtranslator;

public class TranslateLocale {
    private String fromLanguage;
    private String toLanguage;

    public TranslateLocale(String fromLanguage, String toLanguage){
        this.fromLanguage = fromLanguage;
        this.toLanguage = toLanguage;
    }

    public String getFromLanguage() {
        return fromLanguage;
    }

    public void setFromLanguage(String fromLanguage) {
        this.fromLanguage = fromLanguage;
    }

    public String getToLanguage() {
        return toLanguage;
    }

    public void setToLanguage(String toLanguage) {
        this.toLanguage = toLanguage;
    }
}
