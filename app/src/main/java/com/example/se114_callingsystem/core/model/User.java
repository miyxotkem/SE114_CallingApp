package com.example.se114_callingsystem.core.model;

public class User {
    private String userId;      // ID duy nhất từ Firebase Auth
    private String username;    // Tên hiển thị
    private String email;       // Địa chỉ email
    private String profilePic;  // Link ảnh đại diện (URL)
    private String status;      // Trạng thái: "online" hoặc "offline"
    private String coverPic;
    private String bio;
    private String dob;
    private String workplace;
    private String hobbies;
    private java.util.List<String> serverOrder;

    // Constructor trống bắt buộc phải có để Firebase mapping dữ liệu
    public User() {
    }

    // Constructor đầy đủ để tạo user mới khi đăng ký
    public User(String userId, String username, String email) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.status = "online"; // Mặc định khi mới tạo là online
        this.profilePic = "";   // Để trống nếu chưa có ảnh
        this.coverPic = "";
        this.bio = "";
        this.dob = "";
        this.workplace = "";
        this.hobbies = "";
    }

    // Getter và Setter
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

    public java.util.List<String> getServerOrder() { return serverOrder; }
    public void setServerOrder(java.util.List<String> serverOrder) { this.serverOrder = serverOrder; }
}
