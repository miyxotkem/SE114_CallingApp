package com.example.se114_callingsystem.core.model;

public class User {
    private String userId;      // ID duy nháº¥t tá»« Firebase Auth
    private String username;    // TÃªn hiá»ƒn thá»‹
    private String email;       // Äá»‹a chá»‰ email
    private String profilePic;  // Link áº£nh Ä‘áº¡i diá»‡n (URL)
    private String status;      // Tráº¡ng thÃ¡i: "online" hoáº·c "offline"
    private String coverPic;
    private String bio;
    private String dob;
    private String workplace;
    private String hobbies;

    // Constructor trá»‘ng báº¯t buá»™c pháº£i cÃ³ Ä‘á»ƒ Firebase mapping dá»¯ liá»‡u
    public User() {
    }

    // Constructor Ä‘áº§y Ä‘á»§ Ä‘á»ƒ táº¡o user má»›i khi Ä‘Äƒng kÃ½
    public User(String userId, String username, String email) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.status = "online"; // Máº·c Ä‘á»‹nh khi má»›i táº¡o lÃ  online
        this.profilePic = "";   // Äá»ƒ trá»‘ng náº¿u chÆ°a cÃ³ áº£nh
        this.coverPic = "";
        this.bio = "";
        this.dob = "";
        this.workplace = "";
        this.hobbies = "";
    }

    // Getter vÃ  Setter
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getProfilePic() { return profilePic; }
    public void setProfilePic(String profilePic) { this.profilePic = profilePic; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCoverPic() { return coverPic; }
    public void setCoverPic(String coverPic) { this.coverPic = coverPic; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }

    public String getWorkplace() { return workplace; }
    public void setWorkplace(String workplace) { this.workplace = workplace; }

    public String getHobbies() { return hobbies; }
    public void setHobbies(String hobbies) { this.hobbies = hobbies; }
}
