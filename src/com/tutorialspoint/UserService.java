package com.tutorialspoint;  

import java.io.*;
import java.util.*;
import javax.servlet.http.HttpServletResponse;


import javax.ws.rs.*;
import javax.ws.rs.core.*;

@Path("/UserService") 

public class UserService {  
   UserDao userDao = new UserDao();  

    @GET 
   @Path("/users") 
   @Produces(MediaType.APPLICATION_XML) 
   public List<User> getUsers(){ 
      return userDao.getAllUsers(); 
   }

    @GET 
   @Path("/users3") 
   @Produces(MediaType.APPLICATION_JSON) 
   public List<String> getUsers3(){
	//public Vector<User> getUsers3(){
 	List<User> userList = userDao.getAllUsers();
	
	ArrayList<String> q = new ArrayList<String>();
	for(User u: userList) q.add(u.getName());
	/*
	Vector<User> q= new Vector<>();
	for(User u: userList) q.add(u);
	*/
	return q;
   }


    @GET 
   @Path("/users2") 
   @Produces(MediaType.APPLICATION_JSON) 
   public List<User> getUsers2(){
	List<User> userList = userDao.getAllUsers();

	User user = new User(userList.size()+1, "Clock", "Time", "" + new Date());
	userList.add(user); 

	
	return userList;
   }
 
    private static final String SUCCESS_RESULT="<result>success</result>";
    private static final String FAILURE_RESULT="<result>failure</result>";
    private static final String SUCCESS_JSON="success";
    private static final String FAILURE_JSON="failure";

    @POST
   @Path("/users")
   //   @Produces(MediaType.APPLICATION_XML)
   @Produces(MediaType.APPLICATION_JSON)
   @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
   public String createUser(
      @FormParam("name") String name,
      @FormParam("profession") String profession,
      @Context HttpServletResponse servletResponse) throws IOException{
	User user = new User(userDao.getMaxId()+1, name, profession, "unknown");
	int result = userDao.addUser(user);
	//return result == 1 ?  SUCCESS_RESULT: FAILURE_RESULT;
	return result == 1 ?  SUCCESS_JSON: FAILURE_JSON;
   }

}
