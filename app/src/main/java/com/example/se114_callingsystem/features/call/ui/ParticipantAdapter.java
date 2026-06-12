package com.example.se114_callingsystem.features.call.ui;

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
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.se114_callingsystem.core.model.ServerMember;

public class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.CallViewHolder> {

    private Context context;
    private List<Participant> participantList;
    private RtcEngine rtcEngine;
    private List<ServerMember> serverMembers = new java.util.ArrayList<>();
    private java.util.Map<String, String> avatarCache = new java.util.HashMap<>();
    private boolean isInPiPMode = false;
    private int localUid = 0;

    public void setInPiPMode(boolean isInPiPMode) {
        this.isInPiPMode = isInPiPMode;
    }

    public void setLocalUid(int localUid) {
        this.localUid = localUid;
    }

    private Participant getPiPParticipant() {
        if (participantList == null || participantList.isEmpty()) {
            return null;
        }
        int myScreenUid = localUid + 1000;
        // 1. Ưu tiên người đang chia sẻ màn hình (loại trừ màn hình của chính mình để tránh vòng lặp vô hạn)
        for (Participant p : participantList) {
            if ((p.isSharingScreen || p.name.contains("Màn hình")) && p.uid != myScreenUid && !p.name.equals("Màn hình của tôi")) {
                return p;
            }
        }
        // 2. Ưu tiên người đang nói (Active Speaker) và không phải chính mình (nếu có người khác)
        for (Participant p : participantList) {
            if (p.isSpeaking && !p.isMuted && p.uid != localUid && p.uid != myScreenUid) {
                return p;
            }
        }
        // 3. Fallback về người dùng khác trước (không phải chính mình và không phải màn hình của mình)
        for (Participant p : participantList) {
            if (p.uid != localUid && p.uid != myScreenUid) {
                return p;
            }
        }
        // 4. Nếu chỉ có một mình mình
        if (participantList.get(0).uid == myScreenUid && participantList.size() > 1) {
            return participantList.get(1);
        }
        return participantList.get(0);
    }

    public void setServerMembers(List<ServerMember> serverMembers) {
        this.serverMembers = serverMembers != null ? serverMembers : new java.util.ArrayList<>();
        notifyDataSetChanged();
    }

    public ParticipantAdapter(Context context, List<Participant> participantList, RtcEngine rtcEngine) {
        this.context = context;
        this.participantList = participantList;
        this.rtcEngine = rtcEngine;
    }

    private int mParentHeight = 0;

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_call_participant, parent, false);
        if (parent.getHeight() > 0) {
            mParentHeight = parent.getHeight();
        }
        return new CallViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            String payload = payloads.get(0).toString();
            Participant participant = isInPiPMode ? getPiPParticipant() : participantList.get(position);
            if (participant == null) return;

            if (payload.equals("border_update")) {
                updateSpeakingBorder(holder, participant);
                return; // Chỉ cập nhật viền, không vẽ lại video
            }
        }
        // Nếu không có danh sách payloads hoặc sự kiện khác, vẽ lại đầy đủ
        super.onBindViewHolder(holder, position, payloads);
    }

    // --- 2. Hàm Bind đầy đủ ---
    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        Participant participant = isInPiPMode ? getPiPParticipant() : participantList.get(position);
        if (participant == null) return;

        // 0. Tính toán chiều cao động cho khung hình dựa trên kích thước thực tế của parent
        int parentHeight = 0;
        if (holder.itemView.getParent() instanceof ViewGroup) {
            parentHeight = ((ViewGroup) holder.itemView.getParent()).getHeight();
        }
        if (parentHeight == 0) {
            parentHeight = mParentHeight;
        } else {
            mParentHeight = parentHeight; // Cập nhật cache chiều cao mới nhất
        }

        int itemHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
        if (isInPiPMode) {
            itemHeight = ViewGroup.LayoutParams.MATCH_PARENT; // Chiếm trọn chiều cao PiP
        } else if (parentHeight > 0) {
                int videoCount = 0;
                for (Participant p : participantList) {
                    if (!p.isVideoOff) {
                        videoCount++;
                    }
                }
                int voiceCount = participantList.size() - videoCount;

                if (videoCount == 0) {
                    int rows = getRowsCount(voiceCount);
                    itemHeight = parentHeight / rows;
                } else {
                    int videoRows = (videoCount <= 2) ? videoCount : (videoCount + 1) / 2;
                    int voiceRows = (voiceCount + 1) / 2;
                    int voiceHeight = dpToPx(80); // Compact audio height in grid
                    int totalVoiceHeight = voiceRows * voiceHeight;

                    // Limit total voice height to max 40% of parent screen height
                    if (totalVoiceHeight > parentHeight * 0.4) {
                        totalVoiceHeight = (int) (parentHeight * 0.4);
                        if (voiceRows > 0) {
                            voiceHeight = totalVoiceHeight / voiceRows;
                        }
                    }

                    if (participant.isVideoOff) {
                        itemHeight = voiceHeight;
                    } else {
                        itemHeight = (parentHeight - totalVoiceHeight) / videoRows;
                        if (itemHeight < dpToPx(160)) {
                            itemHeight = dpToPx(160); // Minimum video height
                        }
                    }
                }
            }

        ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
        if (lp != null && lp.height != itemHeight) {
            lp.height = itemHeight;
            holder.itemView.setLayoutParams(lp);
        }

        // 1. Dọn dẹp container để tránh chồng chéo khi cuộn RecyclerView
        holder.videoContainer.removeAllViews();

        // 2. Tạo SurfaceView mới
        SurfaceView surfaceView = new SurfaceView(context);
        holder.videoContainer.addView(surfaceView);

        // 3. Thiết lập video từ Agora
        if (rtcEngine != null) {
            boolean isScreenShare = participant.name.equals("Màn hình của tôi") || 
                                    participant.name.startsWith("Màn hình của");

            if (participant.name.equals("Màn hình của tôi")) {
                // ĐÂY LÀ Ô CỦA SCREEN SHARE (Cục bộ)
                VideoCanvas canvas = new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, 0);
                canvas.sourceType = Constants.VIDEO_SOURCE_SCREEN_PRIMARY;
                rtcEngine.setupLocalVideo(canvas);
                rtcEngine.startPreview(Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY);

            } else if (participant.name.contains("Me")) {
                // ĐÂY LÀ Ô CAMERA CỦA BẠN (Cục bộ)
                surfaceView.setZOrderMediaOverlay(true);
                rtcEngine.setupLocalVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0));

            } else {
                // ĐÂY LÀ Ô CỦA NGƯỜI KHÁC
                surfaceView.setZOrderMediaOverlay(true);
                int renderMode = isScreenShare ? VideoCanvas.RENDER_MODE_FIT : VideoCanvas.RENDER_MODE_HIDDEN;
                rtcEngine.setupRemoteVideo(new VideoCanvas(surfaceView, renderMode, participant.uid));
            }
        }

        holder.tvUserName.setText(participant.name);

        // 4. Thiết lập trạng thái hiển thị ban đầu (ẩn/hiện mic, cam, avatar)
        holder.videoContainer.setVisibility(participant.isVideoOff ? View.GONE : View.VISIBLE);
        holder.ivUserProfile.setVisibility(participant.isVideoOff ? View.VISIBLE : View.GONE);
        if (holder.cardAvatar != null) {
            holder.cardAvatar.setVisibility(participant.isVideoOff ? View.VISIBLE : View.GONE);
        }
        holder.ivMuteStatus.setVisibility((isInPiPMode || !participant.isMuted) ? View.GONE : View.VISIBLE);
        holder.tvUserName.setVisibility(isInPiPMode ? View.GONE : View.VISIBLE);

        // PiP style overrides
        ViewGroup.MarginLayoutParams marginLp = (ViewGroup.MarginLayoutParams) holder.cardView.getLayoutParams();
        if (marginLp != null) {
            if (isInPiPMode) {
                marginLp.setMargins(0, 0, 0, 0);
                holder.cardView.setRadius(0);
                holder.cardView.setStrokeWidth(0);
            } else {
                int marginPx = dpToPx(4);
                marginLp.setMargins(marginPx, marginPx, marginPx, marginPx);
                holder.cardView.setRadius(dpToPx(16));
                holder.cardView.setStrokeColor(Color.parseColor("#2D2D4A"));
                holder.cardView.setStrokeWidth(dpToPx(1));
            }
            holder.cardView.setLayoutParams(marginLp);
        }

        bindAvatar(holder, participant);

        updateSpeakingBorder(holder, participant);
    }

    private void bindAvatar(CallViewHolder holder, Participant participant) {
        if (!participant.isVideoOff) {
            return;
        }

        String currentMyUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        int targetUid = participant.uid;
        if (currentMyUid != null && (currentMyUid.hashCode() & 0x7FFFFFFF) + 1000 == targetUid) {
            targetUid = currentMyUid.hashCode() & 0x7FFFFFFF;
        } else if (serverMembers != null) {
            for (ServerMember m : serverMembers) {
                if (m.getUserId() != null && (m.getUserId().hashCode() & 0x7FFFFFFF) + 1000 == targetUid) {
                    targetUid = m.getUserId().hashCode() & 0x7FFFFFFF;
                    break;
                }
            }
        }

        String userId = null;
        if (currentMyUid != null && (currentMyUid.hashCode() & 0x7FFFFFFF) == targetUid) {
            userId = currentMyUid;
        } else if (serverMembers != null) {
            for (ServerMember m : serverMembers) {
                if (m.getUserId() != null && (m.getUserId().hashCode() & 0x7FFFFFFF) == targetUid) {
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
                         .diskCacheStrategy(DiskCacheStrategy.ALL)
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
                                Participant currentPart = isInPiPMode ? getPiPParticipant() : participantList.get(holder.getAdapterPosition());
                                if (currentPart == null) return;
                                int currentTarget = currentPart.uid;
                                if (currentMyUid != null && (currentMyUid.hashCode() & 0x7FFFFFFF) + 1000 == currentTarget) {
                                    currentTarget = currentMyUid.hashCode() & 0x7FFFFFFF;
                                } else if (serverMembers != null) {
                                    for (ServerMember m : serverMembers) {
                                        if (m.getUserId() != null && (m.getUserId().hashCode() & 0x7FFFFFFF) + 1000 == currentTarget) {
                                            currentTarget = m.getUserId().hashCode() & 0x7FFFFFFF;
                                            break;
                                        }
                                    }
                                }
                                if ((finalUserId.hashCode() & 0x7FFFFFFF) == currentTarget) {
                                    if (!profilePic.isEmpty()) {
                                        Glide.with(context)
                                             .load(profilePic)
                                             .placeholder(R.drawable.ic_user)
                                             .diskCacheStrategy(DiskCacheStrategy.ALL)
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
        if (isInPiPMode) {
            if (holder.lottieAudioWave != null) {
                holder.lottieAudioWave.setVisibility(View.GONE);
            }
            if (holder.cardAvatar != null) {
                holder.cardAvatar.setStrokeWidth(0);
            }
            holder.cardView.setStrokeWidth(0);
            return;
        }

        if (participant.isSpeaking && !participant.isMuted) {
            if (participant.isVideoOff) {
                // Speaking with video off: Green border around center avatar & show audio wave Lottie
                if (holder.lottieAudioWave != null) {
                    holder.lottieAudioWave.setVisibility(View.VISIBLE);
                }
                if (holder.cardAvatar != null) {
                    holder.cardAvatar.setStrokeColor(Color.parseColor("#23A559"));
                    holder.cardAvatar.setStrokeWidth(dpToPx(3));
                }
                holder.cardView.setStrokeColor(Color.parseColor("#2D2D4A"));
                holder.cardView.setStrokeWidth(dpToPx(1));
            } else {
                // Speaking with video on: Green border around entire card view & hide audio wave Lottie
                if (holder.lottieAudioWave != null) {
                    holder.lottieAudioWave.setVisibility(View.GONE);
                }
                if (holder.cardAvatar != null) {
                    holder.cardAvatar.setStrokeWidth(0);
                }
                holder.cardView.setStrokeColor(Color.parseColor("#23A559"));
                holder.cardView.setStrokeWidth(dpToPx(3));
            }
        } else {
            // Not speaking: Hide audio wave Lottie & remove green borders
            if (holder.lottieAudioWave != null) {
                holder.lottieAudioWave.setVisibility(View.GONE);
            }
            if (holder.cardAvatar != null) {
                holder.cardAvatar.setStrokeWidth(0);
            }
            holder.cardView.setStrokeColor(Color.parseColor("#2D2D4A"));
            holder.cardView.setStrokeWidth(dpToPx(1));
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        if (isInPiPMode) {
            return (participantList == null || participantList.isEmpty()) ? 0 : 1;
        }
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
        com.airbnb.lottie.LottieAnimationView lottieAudioWave;

        public CallViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            videoContainer = itemView.findViewById(R.id.videoContainer);
            ivUserProfile = itemView.findViewById(R.id.ivUserProfile);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            ivMuteStatus = itemView.findViewById(R.id.ivMuteStatus);
            cardAvatar = itemView.findViewById(R.id.cardAvatar);
            lottieAudioWave = itemView.findViewById(R.id.lottieAudioWave);
        }
    }
}
