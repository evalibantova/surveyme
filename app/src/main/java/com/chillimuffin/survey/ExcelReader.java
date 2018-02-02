package com.chillimuffin.survey;

import android.os.Environment;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class ExcelReader {
   private static final String TAG = "ExcelReader";

   public static ArrayList<String> getQuestions() throws IOException {
        new RemoteLogCat().i(TAG, "getQuestions()");
        ArrayList<String> questions = new ArrayList<String>();
        String excelFilePath = "/sdcard/survey-app/questions.xlsx";
        FileInputStream inputStream = new FileInputStream(new File(excelFilePath));
        new RemoteLogCat().i(TAG, excelFilePath);
        Workbook workbook = getRelevantWorkbook(inputStream, excelFilePath);

        Sheet firstSheet = workbook.getSheetAt(0);
             Iterator<Row> iterator = firstSheet.iterator();

         while (iterator.hasNext()) {
             Row nextRow = iterator.next();
             Iterator<Cell> cellIterator = nextRow.cellIterator();
            // while (cellIterator.hasNext()) {
                 String text = cellIterator.next().getStringCellValue();
                 System.out.print(text);
                 questions.add(text);
             //}
         }

        inputStream.close();
        return questions;
	}

	public static void saveAnswer(String[] values) throws IOException {
        String valuesString = "[";
        for (String value : values) {
            valuesString += value + ",";
        }
        new RemoteLogCat().i(TAG, "saveAnswer(" + valuesString + ")");
		//File file = new File(excelFilePath);
        //file.createNewFile();
		try {
            String path =
                    Environment.getExternalStorageDirectory().toString() + File.separator + "survey-app";
            // Create the folder.
            File folder = new File(path);
            folder.mkdirs();

            // Create the file.
            File file = new File(folder, "answers.xlsx");
			XSSFWorkbook workbook;
			XSSFSheet sheet;
			if(file.exists()) {
				workbook = new XSSFWorkbook(new FileInputStream(file));
				sheet = workbook.getSheetAt(0);
			} else {
				workbook = new XSSFWorkbook();
				sheet = workbook.createSheet("Answers");
			}
			XSSFRow row = sheet.createRow(sheet.getPhysicalNumberOfRows());
            for (int i = 0; i < values.length; i++) {
                row.createCell(i).setCellValue(values[i]);
            }
            try {
                FileOutputStream outputStream = new FileOutputStream(file);
                workbook.write(outputStream);
                workbook.close();
            } catch (Exception e) {
                new RemoteLogCat().i(TAG, e.getMessage());
            }
        } catch (Exception e) {
			new RemoteLogCat().i(TAG, e.getMessage());
		}
	}

 public static void main(String[] args) {
     try {
         getQuestions();
     } catch (IOException e) {
         e.printStackTrace();
     }
 }
 
 private static Workbook getRelevantWorkbook(FileInputStream inputStream, String excelFilePath) throws IOException
 {
     new RemoteLogCat().i(TAG, "getRelevantWorkbook()");
     Workbook workbook = null;
  
     if (excelFilePath.endsWith("xls")) {
         workbook = new HSSFWorkbook(inputStream);
     } else if (excelFilePath.endsWith("xlsx")) {
         workbook = new XSSFWorkbook(inputStream);
     } else {
         throw new IllegalArgumentException("Incorrect file format");
     }
  
     return workbook;
 }

}