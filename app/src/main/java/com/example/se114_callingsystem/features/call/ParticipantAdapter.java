package com.example.se114_callingsystem.features.call;

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
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Participant;
import com.google.android.material.card.MaterialCardView;
import io.agora.rtc2.Constants;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.video.VideoCanvas;
import java.util.List;
import com.bumptech.glide.Glide;
import com.example.se114_callingsystem.core.model.ServerMember;

public class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.CallViewHolder> {

    private Context context;
    private List<Participant> participantList;
    private RtcEngine rtcEngine;
    private List<ServerMember> serverMembers = new java.util.ArrayList<>();
    private java.util.Map<String, String> avatarCache = new java.util.HashMap<>();

    public void setServerMembers(List<ServerMember> serverMembers) {
        this.serverMembers = serverMembers != null ? serverMembers : new java.util.ArrayList<>();
        notifyDataSetChanged();
    }

    public ParticipantAdapter(Context context, List<Participant> participantList, RtcEngine rtcEngine) {
        this.context = context;
        this.participantList = participantList;
        this.rtcEngine = rtcEngine;
    }

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_call_participant, parent, false);

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
                updateSpeakingBorder(holder, participant);
                return; // Chỉ cập nhật viền, không vẽ lại video
            }
            else if (payload.equals("state_update")) {
                // Cập nhật ngay lập tức trạng thái ẩn/hiện cam và mic mà không làm giật hình
                holder.videoContainer.setVisibility(participant.isVideoOff ? View.GONE : View.VISIBLE);
                holder.ivUserProfile.setVisibility(participant.isVideoOff ? View.VISIBLE : View.GONE);
                holder.ivMuteStatus.setVisibility(participant.isMuted ? View.VISIBLE : View.GONE);
                bindAvatar(holder, participant);
                updateSpeakingBorder(holder, participant); // Cập nhật viền xanh ngay khi thay đổi mic
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

        // 1. Dọn dẹp container để tránh chồng chéo khi cuộn RecyclerView
        holder.videoContainer.removeAllViews();

        // 2. Tạo SurfaceView mới
        SurfaceView surfaceView = new SurfaceView(context);
        holder.videoContainer.addView(surfaceView);

        // 3. Thiết lập video từ Agora
        if (rtcEngine != null) {
            if (participant.name.equals("Màn hình của tôi")) {
                // ĐÂY LÀ Ô CỦA SCREEN SHARE (Cục bộ): Cần chỉ định nguồn là màn hình thay vì camera
                // Không set ZOrderMediaOverlay cho screen share để tránh xung đột render
                VideoCanvas canvas = new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, 0);
                canvas.sourceType = Constants.VIDEO_SOURCE_SCREEN_PRIMARY;
                rtcEngine.setupLocalVideo(canvas);
                // QUAN TRỌNG: Phải gọi startPreview với nguồn SCREEN để SDK bắt đầu vẽ khung hình
                rtcEngine.startPreview(Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY);

            } else if (participant.name.contains("Me")) {
                // ĐÂY LÀ Ô CAMERA CỦA BẠN (Cục bộ)
                surfaceView.setZOrderMediaOverlay(true);
                rtcEngine.setupLocalVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0));

            } else {
                // ĐÂY LÀ Ô CỦA NGƯỜI KHÁC (Bao gồm cả camera người khác và màn hình người khác)
                surfaceView.setZOrderMediaOverlay(true);
                // Dùng RENDER_MODE_FIT cho luồng screen share (UID > 1000), RENDER_MODE_HIDDEN cho camera
                int renderMode = (participant.uid >= 1000) ? VideoCanvas.RENDER_MODE_FIT : VideoCanvas.RENDER_MODE_HIDDEN;
                rtcEngine.setupRemoteVideo(new VideoCanvas(surfaceView, renderMode, participant.uid));
            }
        }

        holder.tvUserName.setText(participant.name);

        // 4. Thiết lập trạng thái hiển thị ban đầu (ẩn/hiện mic, cam)
        holder.videoContainer.setVisibility(participant.isVideoOff ? View.GONE : View.VISIBLE);
        holder.ivUserProfile.setVisibility(participant.isVideoOff ? View.VISIBLE : View.GONE);
        holder.ivMuteStatus.setVisibility(participant.isMuted ? View.VISIBLE : View.GONE);
        bindAvatar(holder, participant);

        updateSpeakingBorder(holder, participant);
    }

    private void bindAvatar(CallViewHolder holder, Participant participant) {
        if (!participant.isVideoOff) {
            return;
        }

        String userId = null;
        int targetUid = participant.uid;
        if (targetUid >= 1000) {
            targetUid = targetUid - 1000;
        }

        String currentMyUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (currentMyUid != null && currentMyUid.hashCode() == targetUid) {
            userId = currentMyUid;
        } else if (serverMembers != null) {
            for (ServerMember m : serverMembers) {
                if (m.getUserId() != null && m.getUserId().hashCode() == targetUid) {
                    userId = m.getUserId();
                    break;
                }
            }
        }

        if (userId != null) {
            final String finalUserId = userId;
            String cachedAvatar = avatarCache.get(userId);
            if (cachedAvatar != null) {
                if (!cachedAvatar.isEmpty()) {
                    Glide.with(context)
                         .load(cachedAvatar)
                         .placeholder(R.drawable.ic_user)
                         .into(holder.ivUserProfile);
                } else {
                    holder.ivUserProfile.setImageResource(R.drawable.ic_user);
                }
            } else {
                holder.ivUserProfile.setImageResource(R.drawable.ic_user);
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(userId).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String profilePic = doc.getString("profilePic");
                            if (profilePic == null) profilePic = "";
                            avatarCache.put(finalUserId, profilePic);
                            if (holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                                Participant currentPart = participantList.get(holder.getAdapterPosition());
                                int currentTarget = currentPart.uid >= 1000 ? currentPart.uid - 1000 : currentPart.uid;
                                if (finalUserId.hashCode() == currentTarget) {
                                    if (!profilePic.isEmpty()) {
                                        Glide.with(context)
                                             .load(profilePic)
                                             .placeholder(R.drawable.ic_user)
                                             .into(holder.ivUserProfile);
                                    }
                                }
                            }
                        }
                    });
            }
        } else {
            holder.ivUserProfile.setImageResource(R.drawable.ic_user);
        }
    }

    private void updateSpeakingBorder(CallViewHolder holder, Participant participant) {
        if (holder.cardAvatar != null) {
            holder.cardAvatar.setStrokeWidth(0); // Luôn bỏ viền xung quanh avatar
        }
        
        if (participant.isSpeaking && !participant.isMuted) {
            holder.cardView.setStrokeColor(Color.parseColor("#4CAF50")); // Màu xanh lá sáng
            holder.cardView.setStrokeWidth(dpToPx(4)); // Viền dầy hơn để nổi bật xung quanh ô vuông
        } else {
            holder.cardView.setStrokeColor(Color.parseColor("#2D2D4A")); // Màu xám mặc định
            holder.cardView.setStrokeWidth(dpToPx(1));
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
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
        MaterialCardView cardAvatar;

        public CallViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            videoContainer = itemView.findViewById(R.id.videoContainer);
            ivUserProfile = itemView.findViewById(R.id.ivUserProfile);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            ivMuteStatus = itemView.findViewById(R.id.ivMuteStatus);
            cardAvatar = itemView.findViewById(R.id.cardAvatar);
        }
    }
}

