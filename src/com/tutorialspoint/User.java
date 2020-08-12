package com.tutorialspoint;  

import java.io.Serializable;  
import javax.xml.bind.annotation.XmlElement; 
import javax.xml.bind.annotation.XmlRootElement; 
@XmlRootElement(name = "user") 

public class User implements Serializable {  
   private static final long serialVersionUID = 1L; 
   private int id; 
   private String name; 
   private String profession;  
   private String address;  
   public User(){} 
    
    public User(int id, String name, String profession, String address){  
      this.id = id; 
      this.name = name; 
      this.profession = profession;
      this.address = address;
   }  
   public int getId() { 
      return id; 
   }  
   @XmlElement 
   public void setId(int id) { 
      this.id = id; 
   } 
   public String getName() { 
      return name; 
   } 
   @XmlElement
   public void setName(String name) { 
      this.name = name; 
   } 
   public String getProfession() { 
      return profession; 
   } 
   @XmlElement 
   public void setProfession(String profession) { 
      this.profession = profession; 
   }   
   public String getAddress() { 
      return address; 
   }  
   @XmlElement 
   public void setAddress(String address) { 
      this.address = address; 
   } 
} 
