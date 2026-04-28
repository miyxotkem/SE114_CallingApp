package com.example.se114_callingsystem;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.video.VideoCanvas;

public class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.CallViewHolder> {

    private Context context;
    private List<Participant> participantList;
    private RtcEngine rtcEngine;

    public ParticipantAdapter(Context context, List<Participant> participantList, RtcEngine rtcEngine) {
        this.context = context;
        this.participantList = participantList;
        this.rtcEngine = rtcEngine;
    }

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.activity_item_call_participant, parent, false);

        // Tính toán chiều cao để các ô video chia đều màn hình
        int totalItems = participantList.size();
        int rows = getRowsCount(totalItems);

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (parent.getHeight() > 0) {
            layoutParams.height = parent.getHeight() / rows;
        } else {
            // Backup nếu parent chưa kịp tính height
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        view.setLayoutParams(layoutParams);
        return new CallViewHolder(view);
    }

    // --- 1. Hàm hỗ trợ cập nhật nhanh khi click nút (Payload) ---
    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            String payload = payloads.get(0).toString();
            Participant participant = participantList.get(position);

            if (payload.equals("border_update")) {
                updateSpeakingBorder(holder, participant.isSpeaking);
                return; // Chỉ cập nhật viền, không vẽ lại video
            }
            else if (payload.equals("state_update")) {
                // Cập nhật ngay lập tức trạng thái ẩn/hiện cam và mic mà không làm giật hình
                holder.videoContainer.setVisibility(participant.isVideoOff ? View.GONE : View.VISIBLE);
                holder.ivUserProfile.setVisibility(participant.isVideoOff ? View.VISIBLE : View.GONE);
                holder.ivMuteStatus.setVisibility(participant.isMuted ? View.VISIBLE : View.GONE);
                return;
            }
        }
        // Nếu không có payload, thực hiện bind đầy đủ như bên dưới
        super.onBindViewHolder(holder, position, payloads);
    }

    // --- 2. Hàm Bind đầy đủ (Chạy khi mới vào phòng hoặc lướt danh sách) ---
    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        Participant participant = participantList.get(position);
        holder.tvUserName.setText(participant.name);

        // Dọn dẹp và khởi tạo lại SurfaceView để tránh chồng lấp hình ảnh
        holder.videoContainer.removeAllViews();
        SurfaceView surfaceView = new SurfaceView(context);
        surfaceView.setZOrderMediaOverlay(true); // Đảm bảo video không đè lên thanh điều khiển
        holder.videoContainer.addView(surfaceView);

        if (rtcEngine != null) {
            // NHÃ LƯU Ý: Phân biệt Local (mình) và Remote (người khác)
            if (participant.name.contains("Me")) {
                // Local Video: Luôn dùng UID = 0 cho chính mình
                rtcEngine.setupLocalVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0));
            } else {
                // Remote Video: Dùng đúng UID nhận được từ onUserJoined (ví dụ: 400, 496)
                rtcEngine.setupRemoteVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, participant.uid));
            }
        }

        // Thiết lập trạng thái hiển thị ban đầu dựa trên biến trong Participant
        holder.videoContainer.setVisibility(participant.isVideoOff ? View.GONE : View.VISIBLE);
        holder.ivUserProfile.setVisibility(participant.isVideoOff ? View.VISIBLE : View.GONE);
        holder.ivMuteStatus.setVisibility(participant.isMuted ? View.VISIBLE : View.GONE);

        updateSpeakingBorder(holder, participant.isSpeaking);
    }

    private void updateSpeakingBorder(CallViewHolder holder, boolean isSpeaking) {
        if (isSpeaking) {
            holder.cardView.setStrokeColor(Color.parseColor("#4CAF50")); // Màu xanh lá sáng
            holder.cardView.setStrokeWidth(12);
        } else {
            holder.cardView.setStrokeColor(Color.parseColor("#3A3A3A")); // Màu xám tối
            holder.cardView.setStrokeWidth(2);
        }
    }

    @Override
    public int getItemCount() {
        return participantList.size();
    }

    private int getRowsCount(int totalItems) {
        if (totalItems <= 1) return 1;
        if (totalItems <= 2) return 2;
        if (totalItems <= 4) return 2;
        return 3;
    }

    public static class CallViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        FrameLayout videoContainer;
        ImageView ivUserProfile;
        TextView tvUserName;
        ImageView ivMuteStatus;

        public CallViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            videoContainer = itemView.findViewById(R.id.videoContainer);
            ivUserProfile = itemView.findViewById(R.id.ivUserProfile);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            ivMuteStatus = itemView.findViewById(R.id.ivMuteStatus);
        }
    }
}