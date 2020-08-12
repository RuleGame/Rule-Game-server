package com.tutorialspoint;  

import java.io.File; 
import java.io.FileInputStream; 
import java.io.FileNotFoundException;  
import java.io.FileOutputStream; 
import java.io.IOException; 
import java.io.ObjectInputStream; 
import java.io.ObjectOutputStream; 
import java.util.ArrayList; 
import java.util.List;  

public class UserDao {


    static final String path = "/opt/tomcat/work/tmp/tmp-Users.dat";
    
   public List<User> getAllUsers(){ 
      
      List<User> userList = null; 
      try { 
         File file = new File(path); 
         if (!file.exists()) { 
            userList = new ArrayList<User>(); 

            User user = new User(1, "Bob", "Teacher", "Newcastle"); 
            userList.add(user); 
	    user = new User(2, "Ghenghis", "Khan of Mongolia", "Shangdu"); 
            userList.add(user);
	    user = new User(3, "Peter", "Fisherman", "Tiberias"); 
            userList.add(user);
	    
	    saveUserList(userList); 
         } 
         else{ 
            FileInputStream fis = new FileInputStream(file); 
            ObjectInputStream ois = new ObjectInputStream(fis); 
            userList = (List<User>) ois.readObject(); 
            ois.close(); 
         } 
      } catch (IOException e) { 
         e.printStackTrace(); 
      } catch (ClassNotFoundException e) { 
         e.printStackTrace(); 
      }   
      return userList; 
   } 
   private void saveUserList(List<User> userList){ 
      try {
	  // A relative path being used, the location of the file is the
	  // directory from which "startup.sh" was run... not cool!
	  
         File file = new File(path); 
         FileOutputStream fos;  
         fos = new FileOutputStream(file); 
         ObjectOutputStream oos = new ObjectOutputStream(fos); 
         oos.writeObject(userList); 
         oos.close(); 
      } catch (FileNotFoundException e) { 
         e.printStackTrace(); 
      } catch (IOException e) { 
         e.printStackTrace(); 
      } 
   }

    public int addUser(User pUser){
	List<User> userList = getAllUsers();
	boolean userExists = false;
	for(User user: userList){
	    if(user.getId() == pUser.getId()){
		userExists = true;
		break;
	    }
	}		
	if(!userExists){
	    userList.add(pUser);
	    saveUserList(userList);
	    return 1;
	}
	return 0;
   }

    int getMaxId() {
	int n = 0;
	for(User user: getAllUsers()){
	    if(user.getId()>n) n=user.getId();
	}
	return n;
	
    }


    
}
