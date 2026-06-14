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

    public interface OnParticipantClickListener {
        void onParticipantClick(Participant participant);
    }
    private OnParticipantClickListener onParticipantClickListener;
    public void setOnParticipantClickListener(OnParticipantClickListener listener) {
        this.onParticipantClickListener = listener;
    }

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

        boolean isVideoOff = participant.isVideoOff || participant.isVideoMutedLocally;
        boolean isMuted = participant.isMuted || participant.isMutedLocally;

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
                    boolean pVideoOff = p.isVideoOff || p.isVideoMutedLocally;
                    if (!pVideoOff) {
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

                    if (isVideoOff) {
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

        // 2. Tạo TextureView mới
        android.view.TextureView textureView = new android.view.TextureView(context);
        holder.videoContainer.addView(textureView);

        // Reset color filter for local camera
        textureView.setLayerType(View.LAYER_TYPE_NONE, null);

        // Hide all stickers and warp views by default
        holder.ivStickerCrown.setVisibility(View.GONE);
        holder.ivStickerGlasses.setVisibility(View.GONE);
        holder.ivStickerCatEars.setVisibility(View.GONE);
        holder.ivStickerFrame.setVisibility(View.GONE);
        holder.ivWarpEyeLeft.setVisibility(View.GONE);
        holder.ivWarpEyeRight.setVisibility(View.GONE);
        holder.ivWarpMouth.setVisibility(View.GONE);

        if (participant.name.contains("Me") && !participant.isVideoOff) {
            String selectedSticker = VoiceCallFragment.sSelectedSticker;
            String selectedFilter = VoiceCallFragment.sSelectedFilter;
            
            boolean needsFaceTracking = false;

            if (selectedSticker != null) {
                if (selectedSticker.equals("Frame")) {
                    holder.ivStickerFrame.setVisibility(View.VISIBLE);
                } else if (selectedSticker.equals("Crown") || selectedSticker.equals("Glasses") || selectedSticker.equals("CatEars")) {
                    if (selectedSticker.equals("Crown")) holder.ivStickerCrown.setVisibility(View.VISIBLE);
                    if (selectedSticker.equals("Glasses")) holder.ivStickerGlasses.setVisibility(View.VISIBLE);
                    if (selectedSticker.equals("CatEars")) holder.ivStickerCatEars.setVisibility(View.VISIBLE);
                    needsFaceTracking = true;
                }
            }

            if (selectedFilter != null && !selectedFilter.equals("Normal")) {
                needsFaceTracking = true;
            }

            if (needsFaceTracking) {
                holder.startFaceTracking(textureView);
            } else {
                holder.stopFaceTracking();
            }
        } else {
            holder.stopFaceTracking();
        }

        // 3. Thiết lập video từ Agora
        if (rtcEngine != null) {
            boolean isScreenShare = participant.name.equals("Màn hình của tôi") || 
                                    participant.name.startsWith("Màn hình của");

            if (participant.name.equals("Màn hình của tôi")) {
                // ĐÂY LÀ Ô CỦA SCREEN SHARE (Cục bộ)
                VideoCanvas canvas = new VideoCanvas(textureView, VideoCanvas.RENDER_MODE_FIT, 0);
                canvas.sourceType = Constants.VIDEO_SOURCE_SCREEN_PRIMARY;
                rtcEngine.setupLocalVideo(canvas);
                rtcEngine.startPreview(Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY);

            } else if (participant.name.contains("Me")) {
                // ĐÂY LÀ Ô CAMERA CỦA BẠN (Cục bộ)
                rtcEngine.setupLocalVideo(new VideoCanvas(textureView, VideoCanvas.RENDER_MODE_HIDDEN, 0));

            } else {
                // ĐÂY LÀ Ô CỦA NGƯỜI KHÁC
                int renderMode = isScreenShare ? VideoCanvas.RENDER_MODE_FIT : VideoCanvas.RENDER_MODE_HIDDEN;
                rtcEngine.setupRemoteVideo(new VideoCanvas(textureView, renderMode, participant.uid));
            }
        }

        holder.tvUserName.setText(participant.name);

        // 4. Thiết lập trạng thái hiển thị ban đầu (ẩn/hiện mic, cam, avatar)
        holder.videoContainer.setVisibility(isVideoOff ? View.GONE : View.VISIBLE);
        holder.ivUserProfile.setVisibility(isVideoOff ? View.VISIBLE : View.GONE);
        if (holder.cardAvatar != null) {
            holder.cardAvatar.setVisibility(isVideoOff ? View.VISIBLE : View.GONE);
        }
        holder.ivMuteStatus.setVisibility((isInPiPMode || !isMuted) ? View.GONE : View.VISIBLE);
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

        holder.btnParticipantOptions.setVisibility(isInPiPMode ? View.GONE : View.VISIBLE);

        holder.btnParticipantOptions.setOnClickListener(v -> {
            if (onParticipantClickListener != null) {
                onParticipantClickListener.onParticipantClick(participant);
            }
        });
    }

    private void bindAvatar(CallViewHolder holder, Participant participant) {
        boolean isVideoOff = participant.isVideoOff || participant.isVideoMutedLocally;
        if (!isVideoOff) {
            return;
        }

        String currentMyUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null ? 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        int targetUid = participant.uid;
        if (currentMyUid != null && (currentMyUid.hashCode() & 0x7FFFFFFF) + 1000 == targetUid) {
            targetUid = currentMyUid.hashCode() & 0x7FFFFFFF;
        } else if (VoiceCallFragment.sServerId == null) {
            String otherUid = null;
            if (VoiceCallFragment.sChannelName != null && VoiceCallFragment.sChannelName.startsWith("dm_")) {
                String[] parts = VoiceCallFragment.sChannelName.split("_");
                if (parts.length == 3) {
                    otherUid = parts[1].equals(currentMyUid) ? parts[2] : parts[1];
                }
            }
            if (otherUid != null && (otherUid.hashCode() & 0x7FFFFFFF) + 1000 == targetUid) {
                targetUid = otherUid.hashCode() & 0x7FFFFFFF;
            }
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
        } else if (VoiceCallFragment.sServerId == null) {
            String otherUid = null;
            if (VoiceCallFragment.sChannelName != null && VoiceCallFragment.sChannelName.startsWith("dm_")) {
                String[] parts = VoiceCallFragment.sChannelName.split("_");
                if (parts.length == 3) {
                    otherUid = parts[1].equals(currentMyUid) ? parts[2] : parts[1];
                }
            }
            if (otherUid != null && (otherUid.hashCode() & 0x7FFFFFFF) == targetUid) {
                userId = otherUid;
            }
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

        boolean isVideoOff = participant.isVideoOff || participant.isVideoMutedLocally;
        boolean isMuted = participant.isMuted || participant.isMutedLocally;

        if (participant.isSpeaking && !isMuted) {
            if (isVideoOff) {
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

    private void applyFilterToView(android.view.TextureView textureView, String filterName) {
        textureView.setLayerType(View.LAYER_TYPE_NONE, null);
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
        ImageView ivStickerCrown;
        ImageView ivStickerGlasses;
        ImageView ivStickerCatEars;
        ImageView ivStickerFrame;
        ImageView ivWarpEyeLeft;
        ImageView ivWarpEyeRight;
        ImageView ivWarpMouth;
        ImageView btnParticipantOptions;

        private final android.os.Handler trackingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        private android.view.TextureView activeTextureView;
        private com.google.mlkit.vision.face.FaceDetector faceDetector;
        private boolean isTracking = false;

        private final Runnable trackingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isTracking || activeTextureView == null || !activeTextureView.isAvailable()) {
                    trackingHandler.postDelayed(this, 150);
                    return;
                }

                // Check if video is off
                if (videoContainer.getVisibility() != View.VISIBLE) {
                    trackingHandler.postDelayed(this, 150);
                    return;
                }

                try {
                    // Get a small bitmap from TextureView for fast detection
                    int width = activeTextureView.getWidth();
                    int height = activeTextureView.getHeight();
                    if (width > 0 && height > 0) {
                        // Scaled down to 360px width for fast detection
                        int targetW = 360;
                        int targetH = (height * targetW) / width;
                        android.graphics.Bitmap bitmap = activeTextureView.getBitmap(targetW, targetH);
                        if (bitmap != null) {
                            com.google.mlkit.vision.common.InputImage image = 
                                com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0);
                            
                            if (faceDetector == null) {
                                com.google.mlkit.vision.face.FaceDetectorOptions options =
                                    new com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
                                        .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                                        .setLandmarkMode(com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_ALL)
                                        .build();
                                faceDetector = com.google.mlkit.vision.face.FaceDetection.getClient(options);
                            }

                            faceDetector.process(image)
                                .addOnSuccessListener(faces -> {
                                    if (!isTracking) return;
                                    if (faces != null && !faces.isEmpty()) {
                                        com.google.mlkit.vision.face.Face face = faces.get(0);
                                        updateStickerPositions(face, width, height, targetW, targetH);
                                        updateFaceWarps(face, bitmap, width, height, targetW, targetH);
                                    } else {
                                        hideFaceWarps();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    // Ignore failures
                                });
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                trackingHandler.postDelayed(this, 120); // Check every 120ms
            }
        };

        public void startFaceTracking(android.view.TextureView textureView) {
            this.activeTextureView = textureView;
            if (!isTracking) {
                isTracking = true;
                trackingHandler.post(trackingRunnable);
            }
        }

        public void stopFaceTracking() {
            isTracking = false;
            trackingHandler.removeCallbacks(trackingRunnable);
            if (faceDetector != null) {
                try {
                    faceDetector.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                faceDetector = null;
            }
            // Reset sticker translations and rotations
            resetStickerPositions();
            hideFaceWarps();
        }

        private void resetStickerPositions() {
            // Crown
            ivStickerCrown.setTranslationX(0);
            ivStickerCrown.setTranslationY(0);
            ivStickerCrown.setScaleX(1.0f);
            ivStickerCrown.setScaleY(1.0f);
            ivStickerCrown.setRotation(0);
            // Glasses
            ivStickerGlasses.setTranslationX(0);
            ivStickerGlasses.setTranslationY(0);
            ivStickerGlasses.setScaleX(1.0f);
            ivStickerGlasses.setScaleY(1.0f);
            ivStickerGlasses.setRotation(0);
            // Cat Ears
            ivStickerCatEars.setTranslationX(0);
            ivStickerCatEars.setTranslationY(0);
            ivStickerCatEars.setScaleX(1.0f);
            ivStickerCatEars.setScaleY(1.0f);
            ivStickerCatEars.setRotation(0);
        }

        private int getDpToPx(int dp) {
            if (itemView == null || itemView.getContext() == null) return dp;
            return (int) (dp * itemView.getContext().getResources().getDisplayMetrics().density);
        }

        private void updateStickerPositions(com.google.mlkit.vision.face.Face face, int viewWidth, int viewHeight, int bitmapWidth, int bitmapHeight) {
            // Scale factors
            float scaleX = (float) viewWidth / bitmapWidth;
            float scaleY = (float) viewHeight / bitmapHeight;

            android.graphics.Rect boundingBox = face.getBoundingBox();
            float left = boundingBox.left * scaleX;
            float top = boundingBox.top * scaleY;
            float right = boundingBox.right * scaleX;
            float bottom = boundingBox.bottom * scaleY;

            float faceWidth = right - left;
            float faceHeight = bottom - top;
            float faceCenterX = left + faceWidth / 2f;
            float faceCenterY = top + faceHeight / 2f;

            // Rotation angle around Z axis (roll)
            float rollZ = face.getHeadEulerAngleZ();

            // Crown position: Top of the head
            float crownTargetWidth = faceWidth * 0.9f;
            int crownW = ivStickerCrown.getWidth();
            if (crownW <= 0) crownW = getDpToPx(100);
            float crownWidthScale = crownTargetWidth / crownW;
            ivStickerCrown.setScaleX(crownWidthScale);
            ivStickerCrown.setScaleY(crownWidthScale);
            
            float crownX = faceCenterX - crownW / 2f;
            int crownH = ivStickerCrown.getHeight();
            if (crownH <= 0) crownH = getDpToPx(100);
            float crownY = top - crownH * crownWidthScale * 0.6f;
            ivStickerCrown.setTranslationX(crownX);
            ivStickerCrown.setTranslationY(crownY);
            ivStickerCrown.setRotation(rollZ);

            // Glasses position: Over the eyes
            com.google.mlkit.vision.face.FaceLandmark leftEyeLandmark = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE);
            com.google.mlkit.vision.face.FaceLandmark rightEyeLandmark = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE);

            int glassesW = ivStickerGlasses.getWidth();
            if (glassesW <= 0) glassesW = getDpToPx(100);
            int glassesH = ivStickerGlasses.getHeight();
            if (glassesH <= 0) glassesH = getDpToPx(50);

            if (leftEyeLandmark != null && rightEyeLandmark != null) {
                float eyeLX = leftEyeLandmark.getPosition().x * scaleX;
                float eyeLY = leftEyeLandmark.getPosition().y * scaleY;
                float eyeRX = rightEyeLandmark.getPosition().x * scaleX;
                float eyeRY = rightEyeLandmark.getPosition().y * scaleY;

                float eyesCenterX = (eyeLX + eyeRX) / 2f;
                float eyesCenterY = (eyeLY + eyeRY) / 2f;

                float eyeDistance = (float) Math.sqrt((eyeRX - eyeLX) * (eyeRX - eyeLX) + (eyeRY - eyeLY) * (eyeRY - eyeLY));
                // Glasses should be wider than the eyes distance (about 2.2x)
                float glassesTargetWidth = eyeDistance * 2.2f;
                float glassesWidthScale = glassesTargetWidth / glassesW;
                
                ivStickerGlasses.setScaleX(glassesWidthScale);
                ivStickerGlasses.setScaleY(glassesWidthScale);

                float glassesX = eyesCenterX - glassesW / 2f;
                float glassesY = eyesCenterY - glassesH / 2f;
                ivStickerGlasses.setTranslationX(glassesX);
                ivStickerGlasses.setTranslationY(glassesY);
                ivStickerGlasses.setRotation(rollZ);
            } else {
                // Fallback to face bounds center if landmarks are not ready
                float glassesTargetWidth = faceWidth * 0.7f;
                float glassesWidthScale = glassesTargetWidth / glassesW;
                ivStickerGlasses.setScaleX(glassesWidthScale);
                ivStickerGlasses.setScaleY(glassesWidthScale);

                float glassesX = faceCenterX - glassesW / 2f;
                float glassesY = faceCenterY - faceHeight * 0.15f - glassesH / 2f;
                ivStickerGlasses.setTranslationX(glassesX);
                ivStickerGlasses.setTranslationY(glassesY);
                ivStickerGlasses.setRotation(rollZ);
            }

            // Cat Ears: Top corner of the head
            float earsTargetWidth = faceWidth * 1.1f;
            int earsW = ivStickerCatEars.getWidth();
            if (earsW <= 0) earsW = getDpToPx(160);
            float earsWidthScale = earsTargetWidth / earsW;
            ivStickerCatEars.setScaleX(earsWidthScale);
            ivStickerCatEars.setScaleY(earsWidthScale);

            float earsX = faceCenterX - earsW / 2f;
            int earsH = ivStickerCatEars.getHeight();
            if (earsH <= 0) earsH = getDpToPx(70);
            float earsY = top - earsH * earsWidthScale * 0.55f;
            ivStickerCatEars.setTranslationX(earsX);
            ivStickerCatEars.setTranslationY(earsY);
            ivStickerCatEars.setRotation(rollZ);
        }

        private void hideFaceWarps() {
            ivWarpEyeLeft.setVisibility(View.GONE);
            ivWarpEyeRight.setVisibility(View.GONE);
            ivWarpMouth.setVisibility(View.GONE);
        }

        private void updateFaceWarps(com.google.mlkit.vision.face.Face face, android.graphics.Bitmap bitmap, int viewWidth, int viewHeight, int bitmapWidth, int bitmapHeight) {
            String selectedFilter = VoiceCallFragment.sSelectedFilter;
            if (selectedFilter == null || selectedFilter.equals("Normal")) {
                hideFaceWarps();
                return;
            }

            // Scale factors
            float scaleX = (float) viewWidth / bitmapWidth;
            float scaleY = (float) viewHeight / bitmapHeight;

            android.graphics.Rect boundingBox = face.getBoundingBox();
            float left = boundingBox.left;
            float top = boundingBox.top;
            float right = boundingBox.right;
            float bottom = boundingBox.bottom;

            float faceWidth = right - left;
            float faceHeight = bottom - top;
            float faceCenterX = left + faceWidth / 2f;
            float faceCenterY = top + faceHeight / 2f;

            // Get landmarks
            com.google.mlkit.vision.face.FaceLandmark leftEyeLandmark = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE);
            com.google.mlkit.vision.face.FaceLandmark rightEyeLandmark = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE);
            com.google.mlkit.vision.face.FaceLandmark mouthBottomLandmark = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_BOTTOM);
            com.google.mlkit.vision.face.FaceLandmark mouthLeftLandmark = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_LEFT);
            com.google.mlkit.vision.face.FaceLandmark mouthRightLandmark = face.getLandmark(com.google.mlkit.vision.face.FaceLandmark.MOUTH_RIGHT);

            // Warp factor settings based on filter
            float eyeScale = 1.0f;
            float mouthScale = 1.0f;
            boolean showEyes = false;
            boolean showMouth = false;

            if (selectedFilter.equals("ApeFace")) {
                eyeScale = 1.8f;
                mouthScale = 2.4f;
                showEyes = true;
                showMouth = true;
            } else if (selectedFilter.equals("BigEyes")) {
                eyeScale = 2.3f;
                showEyes = true;
            } else if (selectedFilter.equals("BigMouth")) {
                mouthScale = 2.6f;
                showMouth = true;
            } else if (selectedFilter.equals("Alien")) {
                eyeScale = 2.4f;
                mouthScale = 0.6f;
                showEyes = true;
                showMouth = true;
            } else if (selectedFilter.equals("SmallEyes")) {
                eyeScale = 0.4f;
                showEyes = true;
            } else if (selectedFilter.equals("SmallMouth")) {
                mouthScale = 0.4f;
                showMouth = true;
            }

            // --- 1. Left Eye Warp ---
            if (showEyes && leftEyeLandmark != null) {
                float eyeX = leftEyeLandmark.getPosition().x;
                float eyeY = leftEyeLandmark.getPosition().y;
                float cropRadius = faceWidth * 0.13f; // Size of eye crop
                
                android.graphics.Bitmap eyeCrop = cropAndScaleWarp(bitmap, eyeX, eyeY, cropRadius, true);
                if (eyeCrop != null) {
                    ivWarpEyeLeft.setImageBitmap(eyeCrop);
                    
                    float targetW = cropRadius * 2 * scaleX * eyeScale;
                    float targetH = cropRadius * 2 * scaleY * eyeScale;
                    
                    ViewGroup.LayoutParams lp = ivWarpEyeLeft.getLayoutParams();
                    lp.width = (int) targetW;
                    lp.height = (int) targetH;
                    ivWarpEyeLeft.setLayoutParams(lp);
                    
                    ivWarpEyeLeft.setTranslationX(eyeX * scaleX - targetW / 2f);
                    ivWarpEyeLeft.setTranslationY(eyeY * scaleY - targetH / 2f);
                    ivWarpEyeLeft.setVisibility(View.VISIBLE);
                } else {
                    ivWarpEyeLeft.setVisibility(View.GONE);
                }
            } else {
                ivWarpEyeLeft.setVisibility(View.GONE);
            }

            // --- 2. Right Eye Warp ---
            if (showEyes && rightEyeLandmark != null) {
                float eyeX = rightEyeLandmark.getPosition().x;
                float eyeY = rightEyeLandmark.getPosition().y;
                float cropRadius = faceWidth * 0.13f;
                
                android.graphics.Bitmap eyeCrop = cropAndScaleWarp(bitmap, eyeX, eyeY, cropRadius, true);
                if (eyeCrop != null) {
                    ivWarpEyeRight.setImageBitmap(eyeCrop);
                    
                    float targetW = cropRadius * 2 * scaleX * eyeScale;
                    float targetH = cropRadius * 2 * scaleY * eyeScale;
                    
                    ViewGroup.LayoutParams lp = ivWarpEyeRight.getLayoutParams();
                    lp.width = (int) targetW;
                    lp.height = (int) targetH;
                    ivWarpEyeRight.setLayoutParams(lp);
                    
                    ivWarpEyeRight.setTranslationX(eyeX * scaleX - targetW / 2f);
                    ivWarpEyeRight.setTranslationY(eyeY * scaleY - targetH / 2f);
                    ivWarpEyeRight.setVisibility(View.VISIBLE);
                } else {
                    ivWarpEyeRight.setVisibility(View.GONE);
                }
            } else {
                ivWarpEyeRight.setVisibility(View.GONE);
            }

            // --- 3. Mouth Warp ---
            if (showMouth) {
                float mX = faceCenterX;
                float mY = bottom - faceHeight * 0.2f;
                float mW = faceWidth * 0.38f;
                float mH = faceHeight * 0.20f;

                if (mouthLeftLandmark != null && mouthRightLandmark != null) {
                    float mLX = mouthLeftLandmark.getPosition().x;
                    float mRX = mouthRightLandmark.getPosition().x;
                    mW = (mRX - mLX) * 1.4f;
                    mX = (mLX + mRX) / 2f;
                    
                    if (mouthBottomLandmark != null) {
                        mY = mouthBottomLandmark.getPosition().y - mH * 0.25f;
                    }
                }

                android.graphics.Bitmap mouthCrop = cropMouthWarp(bitmap, mX, mY, mW, mH);
                if (mouthCrop != null) {
                    ivWarpMouth.setImageBitmap(mouthCrop);
                    
                    float targetW = mW * scaleX * mouthScale;
                    float targetH = mH * scaleY * mouthScale;
                    
                    ViewGroup.LayoutParams lp = ivWarpMouth.getLayoutParams();
                    lp.width = (int) targetW;
                    lp.height = (int) targetH;
                    ivWarpMouth.setLayoutParams(lp);
                    
                    ivWarpMouth.setTranslationX(mX * scaleX - targetW / 2f);
                    ivWarpMouth.setTranslationY(mY * scaleY - targetH / 2f);
                    ivWarpMouth.setVisibility(View.VISIBLE);
                } else {
                    ivWarpMouth.setVisibility(View.GONE);
                }
            } else {
                ivWarpMouth.setVisibility(View.GONE);
            }
        }

        private android.graphics.Bitmap cropAndScaleWarp(android.graphics.Bitmap src, float cx, float cy, float radius, boolean circle) {
            int left = (int) (cx - radius);
            int top = (int) (cy - radius);
            int size = (int) (radius * 2);

            // Ensure bounds
            if (left < 0) left = 0;
            if (top < 0) top = 0;
            if (left + size > src.getWidth()) size = src.getWidth() - left;
            if (top + size > src.getHeight()) size = src.getHeight() - top;

            if (size <= 0) return null;

            android.graphics.Bitmap crop = android.graphics.Bitmap.createBitmap(src, left, top, size, size);
            if (circle) {
                return getCircularBitmap(crop);
            }
            return crop;
        }

        private android.graphics.Bitmap cropMouthWarp(android.graphics.Bitmap src, float cx, float cy, float w, float h) {
            int left = (int) (cx - w / 2f);
            int top = (int) (cy - h / 2f);
            int width = (int) w;
            int height = (int) h;

            // Ensure bounds
            if (left < 0) left = 0;
            if (top < 0) top = 0;
            if (left + width > src.getWidth()) width = src.getWidth() - left;
            if (top + height > src.getHeight()) height = src.getHeight() - top;

            if (width <= 0 || height <= 0) return null;

            android.graphics.Bitmap crop = android.graphics.Bitmap.createBitmap(src, left, top, width, height);
            return getOvalBitmap(crop);
        }

        private android.graphics.Bitmap getCircularBitmap(android.graphics.Bitmap bitmap) {
            int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
            android.graphics.Bitmap output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(output);

            final android.graphics.Paint paint = new android.graphics.Paint();
            final android.graphics.Rect rect = new android.graphics.Rect(0, 0, size, size);

            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0);
            paint.setColor(0xff424242);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bitmap, rect, rect, paint);
            return output;
        }

        private android.graphics.Bitmap getOvalBitmap(android.graphics.Bitmap bitmap) {
            android.graphics.Bitmap output = android.graphics.Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(output);

            final android.graphics.Paint paint = new android.graphics.Paint();
            final android.graphics.Rect rect = new android.graphics.Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
            final android.graphics.RectF rectF = new android.graphics.RectF(rect);

            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0);
            paint.setColor(0xff424242);
            canvas.drawOval(rectF, paint);
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(bitmap, rect, rect, paint);
            return output;
        }

        public CallViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            videoContainer = itemView.findViewById(R.id.videoContainer);
            ivUserProfile = itemView.findViewById(R.id.ivUserProfile);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            ivMuteStatus = itemView.findViewById(R.id.ivMuteStatus);
            cardAvatar = itemView.findViewById(R.id.cardAvatar);
            lottieAudioWave = itemView.findViewById(R.id.lottieAudioWave);
            ivStickerCrown = itemView.findViewById(R.id.ivStickerCrown);
            ivStickerGlasses = itemView.findViewById(R.id.ivStickerGlasses);
            ivStickerCatEars = itemView.findViewById(R.id.ivStickerCatEars);
            ivStickerFrame = itemView.findViewById(R.id.ivStickerFrame);
            ivWarpEyeLeft = itemView.findViewById(R.id.ivWarpEyeLeft);
            ivWarpEyeRight = itemView.findViewById(R.id.ivWarpEyeRight);
            ivWarpMouth = itemView.findViewById(R.id.ivWarpMouth);
            btnParticipantOptions = itemView.findViewById(R.id.btnParticipantOptions);
        }
    }
}
