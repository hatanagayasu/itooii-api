package controllers;

import play.*;
import play.mvc.*;

import java.io.File;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MediaController extends AppController {

    private static String bucketName = "itooii_test1";
    private static String KeyID = "AKIAJGUFVNUVZXWLETZQ";
    private static String AccessKey = "BE1EGqQfzZEUVXZiy9HgsBferHEaBQuoNQ3402Ct";

    public static Result upload(JsonNode params) {

        //File file = request().body().asRaw().asFile();
        //response().setHeader("Access-Control-Allow-Origin", "*");
        /*
         * JSONArray json = new JSONArray(); try { List<FileItem> items =
         * uploadHandler.parseRequest(request); for (FileItem item : items) { if
         * (!item.isFormField()) { File file = new File(fileUploadPath, item.getName());
         * item.write(file); JSONObject jsono = new JSONObject(); jsono.put("name", item.getName());
         * jsono.put("size", item.getSize()); jsono.put("url", "upload?getfile=" + item.getName());
         * jsono.put("thumbnail_url", "upload?getthumb=" + item.getName()); jsono.put("delete_url",
         * "upload?delfile=" + item.getName()); jsono.put("delete_type", "GET"); json.put(jsono); }
         * } }
         */

        ObjectNode res = mapper.createObjectNode();
        ArrayNode files = res.putArray("files");

        Logger.info("enter!");
        Http.MultipartFormData body = request().body().asMultipartFormData();

        for (Http.MultipartFormData.FilePart part : body.getFiles()) {
            Logger.debug(part.getFilename());
            Logger.debug(part.getKey());
            Logger.debug(part.getContentType());
            Logger.debug(part.getFile().getName());
            Logger.debug(part.getFile().getAbsolutePath());
            Logger.debug(String.valueOf(part.getFile().getTotalSpace()));
            move2s3(part.getFile().getAbsolutePath(), part.getFilename());

            ObjectNode file = files.addObject();
            file.put("name", part.getFile().getName());
            file.put("size", part.getFile().getTotalSpace());
            file.put("url", "upload?getfile=" + part.getFile().getName());
            file.put("thumbnail_url", "upload?getthumb=" + part.getFile().getName());
            file.put("delete_url", "upload?delfile=" + part.getFile().getName());
            file.put("delete_type", "GET");
        }

        errorlog(res);

        return Ok(res);
    }

    private static boolean move2s3(String src_path, String targetName) {
        AWSCredentials credentials = new BasicAWSCredentials(KeyID, AccessKey);

        //AmazonS3 s3client = new AmazonS3Client(new ProfileCredentialsProvider());
        AmazonS3 s3client = new AmazonS3Client(credentials);

        try {
            System.out.println("Uploading a new object to S3 from a file\n");
            //File file = new File(uploadFileName);

            //String fileName = folderName + SUFFIX + "testvideo.mp4";
            String path = System.getProperty("user.dir");
            Logger.debug("user.dir:" + path);

            s3client.putObject(new PutObjectRequest(bucketName, targetName, new File(src_path)));

            //s3client.putObject(new PutObjectRequest(
            //	                 bucketName, keyName, file));

        } catch (AmazonServiceException ase) {
            Logger.debug("Caught an AmazonServiceException, which " + "means your request made it "
                + "to Amazon S3, but was rejected with an error response"
                + " for some reason.");
            Logger.debug("Error Message:    " + ase.getMessage());
            Logger.debug("HTTP Status Code: " + ase.getStatusCode());
            Logger.debug("AWS Error Code:   " + ase.getErrorCode());
            Logger.debug("Error Type:       " + ase.getErrorType());
            Logger.debug("Request ID:       " + ase.getRequestId());
        } catch (AmazonClientException ace) {
            Logger.debug("Caught an AmazonClientException, which "
                + "means the client encountered "
                + "an internal error while trying to " + "communicate with S3, "
                + "such as not being able to access the network.");
            Logger.debug("Error Message: " + ace.getMessage());
        }

        return true;
    }

}
