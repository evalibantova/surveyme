package com.chillimuffin.survey;

import java.io.IOException;
import java.util.ArrayList;

public class Questions {
	private static final String TAG = "Questions";
	private ArrayList<String> questions;
	private int length;

	public Questions() throws IOException {
		super();
		length = 0;
		this.questions = ExcelReader.getQuestions();
		for (String q : questions){
			length++;
		}
	}
	
	public String getQuestion(int id){
		new RemoteLogCat().i(TAG, "getQuestion(" + id + ")");
		return questions.get(id);
	}

	public void saveAnswer(String[] values) throws IOException {
		String valuesString = "[";
		for (String value : values) {
			valuesString += value + ",";
		}
		new RemoteLogCat().i(TAG, "saveAnswer(" + valuesString + ")");
        ExcelReader.saveAnswer(values);
	}

	public int getLength() {
		return length;
	}

}
