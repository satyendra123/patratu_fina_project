package com.timmy.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import com.timmy.entity.EnrollInfo;
import com.timmy.entity.MachineCommand;
import com.timmy.entity.Temp;
import com.timmy.mapper.EnrollInfoMapper;
import com.timmy.mapper.MachineCommandMapper;

public class  ImageProcess {

	@Autowired
	EnrollInfoMapper enrollInfoMapper;
	
	
	@Autowired
	MachineCommandMapper machineCommandMapper;
	 public static boolean base64toImage(String base64String, String picName) {
	     //   String savePath = InitializationCfg.getCfg("attachment.path");
	        Date now = new Date();
		/*
		 * SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd"); String
		 * subPath = dateFormat.format(now);
		 */
	        String imagePath = "C:/dynamicface/picture/";
	        String file = picName + ".jpg";
	        System.out.println("图片路径" + imagePath + file);
	        File file2 = new File(imagePath + file);
	        if (base64String == null) {
	            return false;
	        } else {
	            try {
	                if (!file2.exists()) {
	                	File parent = file2.getParentFile();
	                	if (parent != null && !parent.exists() && !parent.mkdirs()) {
	                		throw new IOException("Failed to create directory: " + parent.getAbsolutePath());
	                	}
	                    file2.createNewFile();
	                }

	                String normalized = base64String.trim();
	                if (normalized.startsWith("data:")) {
	                	int commaIndex = normalized.indexOf(',');
	                	if (commaIndex >= 0 && commaIndex + 1 < normalized.length()) {
	                		normalized = normalized.substring(commaIndex + 1);
	                	}
	                }
	                normalized = normalized.replaceAll("\\s+", "");
	                byte[] b = Base64.getDecoder().decode(normalized);

	                OutputStream out = new FileOutputStream(file2);
	                out.write(b);
	                out.flush();
	                out.close();
	                return true;
	            } catch (Exception e) {
	                e.printStackTrace();
	                return false;
	            }
	        }
	    }
	 
	  public static String multipartFileToBASE64(MultipartFile mFile) throws Exception{
	        String[] suffixArra=mFile.getOriginalFilename().split("\\.");
	        String preffix="data:image/jpg;base64,".replace("jpg", suffixArra[suffixArra.length - 1]);
	        String base64EncoderImg=preffix + Base64.getEncoder().encodeToString(mFile.getBytes());
	        return base64EncoderImg;
	    }
	  
	  /**
	   * 图片转base64字符串
	   * @param imgFile 图片路径
	   * @return
	   */
	  public static String imageToBase64Str(String imgFile) {
	   InputStream inputStream = null;
	   byte[] data = null;
	   try {
	    inputStream = new FileInputStream(imgFile);
	    data = new byte[inputStream.available()];
	    inputStream.read(data);
	    inputStream.close();
	   } catch (IOException e) {
	    e.printStackTrace();
	   }
	   // 加密
		/*
		 * String s=encoder.encode(data); String s2=s.replaceAll("[+]", "%2B");
		 */
	   if (data == null) {
		   return "";
	   }
	   return Base64.getEncoder().encodeToString(data);
	  }
	  
	  
	  public static void main(String[] args) {

	 
		  
	}
}
