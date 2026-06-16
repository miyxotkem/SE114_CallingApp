package com.example.se114_callingsystem.features.chat.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.SeekBar;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.example.se114_callingsystem.R;
import com.example.se114_callingsystem.core.model.Message;
import com.example.se114_callingsystem.core.model.ServerMember;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;
    private static final int TYPE_REMINDER = 3;

    private List<Message> mMessages;
    private static FirebaseFirestore db;
    private String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "UNKNOWN";
    private OnChatInteractListener listener;
    private String serverColor = "#6C63FF";
    private List<String> serverMemberNames = new java.util.ArrayList<>();
    private List<ServerMember> serverMembers = new java.util.ArrayList<>();
    private String highlightMessageId = null;
    private static final java.util.Map<String, String> avatarCache = new java.util.HashMap<>();
    private static final java.util.Map<String, String> audioDurationCache = new java.util.HashMap<>();

    public void setHighlightMessageId(String messageId) {
        this.highlightMessageId = messageId;
    }

    public String getHighlightMessageId() {
        return highlightMessageId;
    }

    public interface OnChatInteractListener {
        void onReply(Message message);
        void onDelete(Message message);
        void onReact(Message message, String emoji);
        void onPinToggle(Message message);
        void onEditReminder(Message message);
        void onRepliedMessageClick(Message message);
    }

    public ChatAdapter(List<Message> messages, String serverColor, OnChatInteractListener listener) {
        this.mMessages = messages;
        this.serverColor = serverColor;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void setServerMemberNames(List<String> names) {
        this.serverMemberNames = names != null ? names : new java.util.ArrayList<>();
        notifyDataSetChanged();
    }

    public void setServerMembers(List<ServerMember> members) {
        this.serverMembers = members != null ? members : new java.util.ArrayList<>();
        List<String> names = new java.util.ArrayList<>();
        for (ServerMember m : this.serverMembers) {
            if (m.getUserName() != null && !m.getUserName().trim().isEmpty()) {
                names.add(m.getUserName());
            }
            if (m.getNickname() != null && !m.getNickname().trim().isEmpty()) {
                names.add(m.getNickname());
            }
        }
        setServerMemberNames(names);
    }

    @Override
    public int getItemViewType(int position) {
        if ("reminder".equals(mMessages.get(position).getType())) {
            return TYPE_REMINDER;
        }
        if (mMessages.get(position).getSenderId().equals(currentUserId)) {
            return TYPE_SENT;
        } else {
            return TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_REMINDER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_reminder, parent, false);
            return new ReminderViewHolder(view);
        } else if (viewType == TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message_sent, parent, false);
            return new SentMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message_received, parent, false);
            return new ReceivedMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = mMessages.get(position);
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();

        // Lấy density để quy đổi dp sang px
        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;

        // 1. Logic gom nhóm tin nhắn
        boolean isFirstInGroup = true;
        if (position > 0) {
            Message previousMsg = mMessages.get(position - 1);
            if (previousMsg.getSenderId().equals(message.getSenderId())) {
                isFirstInGroup = false;
            }
        }

        boolean isLastInGroup = true;
        if (position < mMessages.size() - 1) {
            Message nextMsg = mMessages.get(position + 1);
            if (nextMsg.getSenderId().equals(message.getSenderId())) {
                isLastInGroup = false;
            }
        }

        // 2. Logic điều chỉnh Margin động dựa trên nhóm tin nhắn
        if (isFirstInGroup) {
            params.topMargin = (int) (8 * density);
        } else {
            params.topMargin = (int) (1 * density);
        }
        holder.itemView.setLayoutParams(params);

        // 3. Gọi hàm bind như bình thường
        if (holder instanceof ReminderViewHolder) {
            ((ReminderViewHolder) holder).bind(message, serverColor, listener);
        } else if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(message, mMessages, listener, currentUserId, isLastInGroup, serverColor, serverMembers, highlightMessageId, this);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(message, mMessages, isFirstInGroup, isLastInGroup, listener, currentUserId, serverColor, serverMembers, highlightMessageId, this);
        }
    }

    @Override
    public int getItemCount() {
        return mMessages != null ? mMessages.size() : 0;
    }

    // --- VIEWHOLDERS ---

    public static class ReminderViewHolder extends RecyclerView.ViewHolder {
        TextView tvReminderContent, tvReminderTime;
        com.google.android.material.card.MaterialCardView cardReminder;

        public ReminderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvReminderContent = itemView.findViewById(R.id.tvReminderContent);
            tvReminderTime = itemView.findViewById(R.id.tvReminderTime);
            cardReminder = itemView.findViewById(R.id.cardReminder);
        }

        void bind(Message message, String serverColor, OnChatInteractListener listener) {
            android.graphics.Typeface inter = androidx.core.content.res.ResourcesCompat.getFont(tvReminderContent.getContext(), R.font.inter);
            if (message.isDeleted()) {
                tvReminderContent.setText("Lời nhắc đã bị xóa");
                tvReminderContent.setTypeface(inter, android.graphics.Typeface.ITALIC);
                tvReminderTime.setVisibility(View.GONE);
                cardReminder.setOnClickListener(null);
                cardReminder.setOnLongClickListener(null);
            } else {
                tvReminderContent.setText(message.getContent());
                tvReminderContent.setTypeface(inter, android.graphics.Typeface.NORMAL);
                tvReminderTime.setVisibility(View.VISIBLE);
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                tvReminderTime.setText(sdf.format(new Date(message.getReminderTime())));
                
                cardReminder.setOnLongClickListener(v -> {
                    if (message.getReminderTime() < System.currentTimeMillis()) {
                        android.widget.Toast.makeText(v.getContext(), "Không thể sửa hoặc xóa lời nhắc đã qua", android.widget.Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    String[] options = {"✏️ Sửa lời nhắc", "🗑️ Xóa lời nhắc"};
                    com.example.se114_callingsystem.core.util.BottomSheetUtils.showListDialog(
                            v.getContext(),
                            "Tùy chọn lời nhắc",
                            options,
                            (index, option) -> {
                                if (index == 0) {
                                    listener.onEditReminder(message);
                                } else {
                                    listener.onDelete(message);
                                }
                            }
                    );
                    return true;
                });
            }
            
            try {
                int color = Color.parseColor(serverColor);
                cardReminder.setStrokeColor(color);
            } catch (Exception e) {}
        }
    }

    public static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, textReaction, textRepliedTo, textTime, tvFileName, tvReplyHeader, tvAudioTime;
        ImageView ivMessageImage, ivRepliedImage, btnPlayPause;
        LinearLayout layoutWaveform;
        LinearLayout layoutFile, layoutPinnedIndicator, layoutAudio;
        View cardBubble, layoutRepliedContainer;
        View layoutVideo;
        ImageView ivVideoThumbnail;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessage);
            textReaction = itemView.findViewById(R.id.textReaction);
            textRepliedTo = itemView.findViewById(R.id.textRepliedTo);
            cardBubble = itemView.findViewById(R.id.cardBubble);
            textTime = itemView.findViewById(R.id.textTime);
            ivMessageImage = itemView.findViewById(R.id.ivMessageImage);
            ivRepliedImage = itemView.findViewById(R.id.ivRepliedImage);
            layoutFile = itemView.findViewById(R.id.layoutFile);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            layoutPinnedIndicator = itemView.findViewById(R.id.layoutPinnedIndicator);
            layoutRepliedContainer = itemView.findViewById(R.id.layoutRepliedContainer);
            tvReplyHeader = itemView.findViewById(R.id.tvReplyHeader);
            layoutAudio = itemView.findViewById(R.id.layoutAudio);
            btnPlayPause = itemView.findViewById(R.id.btnPlayPause);
            layoutWaveform = itemView.findViewById(R.id.layoutWaveform);
            tvAudioTime = itemView.findViewById(R.id.tvAudioTime);
            layoutVideo = itemView.findViewById(R.id.layoutVideo);
            ivVideoThumbnail = itemView.findViewById(R.id.ivVideoThumbnail);
        }

        void bind(Message message, List<Message> messages, OnChatInteractListener listener, String currentUserId, boolean isLastInGroup, String serverColor, List<ServerMember> serverMembers, String highlightMessageId, ChatAdapter adapter) {
            bindSharedLogic(message, messageText, ivMessageImage, layoutVideo, ivVideoThumbnail, layoutFile, tvFileName, layoutAudio, btnPlayPause, layoutWaveform, tvAudioTime, textReaction, layoutRepliedContainer, textRepliedTo, ivRepliedImage, cardBubble, layoutPinnedIndicator, tvReplyHeader, messages, listener, currentUserId, serverColor, serverMembers, highlightMessageId, adapter);

            if (isLastInGroup && textTime != null) {
                textTime.setVisibility(View.VISIBLE);
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                textTime.setText(sdf.format(new Date(message.getTimestamp())));
            } else if (textTime != null) {
                textTime.setVisibility(View.GONE);
            }
        }
    }

    public java.util.Map<String, String> planCache = new java.util.HashMap<>();

    public static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, senderName, textTime, textReaction, textRepliedTo, tvFileName, tvReplyHeader, tvAudioTime;
        ImageView avatarImg, ivMessageImage, ivRepliedImage, btnPlayPause;
        LinearLayout layoutWaveform;
        LinearLayout layoutFile, layoutPinnedIndicator, layoutAudio;
        View cardBubble, layoutRepliedContainer;
        View layoutVideo;
        ImageView ivVideoThumbnail;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessage);
            senderName = itemView.findViewById(R.id.textSenderName);
            textTime = itemView.findViewById(R.id.textTime);
            textReaction = itemView.findViewById(R.id.textReaction);
            textRepliedTo = itemView.findViewById(R.id.textRepliedTo);
            cardBubble = itemView.findViewById(R.id.cardBubble);
            avatarImg = itemView.findViewById(R.id.imgAvatar);
            ivMessageImage = itemView.findViewById(R.id.ivMessageImage);
            ivRepliedImage = itemView.findViewById(R.id.ivRepliedImage);
            layoutFile = itemView.findViewById(R.id.layoutFile);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            layoutPinnedIndicator = itemView.findViewById(R.id.layoutPinnedIndicator);
            layoutRepliedContainer = itemView.findViewById(R.id.layoutRepliedContainer);
            tvReplyHeader = itemView.findViewById(R.id.tvReplyHeader);
            layoutAudio = itemView.findViewById(R.id.layoutAudio);
            btnPlayPause = itemView.findViewById(R.id.btnPlayPause);
            layoutWaveform = itemView.findViewById(R.id.layoutWaveform);
            tvAudioTime = itemView.findViewById(R.id.tvAudioTime);
            layoutVideo = itemView.findViewById(R.id.layoutVideo);
            ivVideoThumbnail = itemView.findViewById(R.id.ivVideoThumbnail);
        }

        private void applyAvatarBorder(ImageView avatarImg, String plan) {
            if (avatarImg instanceof com.google.android.material.imageview.ShapeableImageView) {
                com.google.android.material.imageview.ShapeableImageView siv = (com.google.android.material.imageview.ShapeableImageView) avatarImg;
                float density = siv.getResources().getDisplayMetrics().density;
                if ("Pro".equals(plan)) {
                    siv.setStrokeWidth(2f * density);
                    siv.setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFD700")));
                    int padding = (int)(2 * density);
                    siv.setPadding(padding, padding, padding, padding);
                } else {
                    siv.setStrokeWidth(0f);
                    siv.setPadding(0, 0, 0, 0);
                }
            }
        }

        void bind(Message message, List<Message> messages, boolean isFirstInGroup, boolean isLastInGroup, OnChatInteractListener listener, String currentUserId, String serverColor, List<ServerMember> serverMembers, String highlightMessageId, ChatAdapter adapter) {
            bindSharedLogic(message, messageText, ivMessageImage, layoutVideo, ivVideoThumbnail, layoutFile, tvFileName, layoutAudio, btnPlayPause, layoutWaveform, tvAudioTime, textReaction, layoutRepliedContainer, textRepliedTo, ivRepliedImage, cardBubble, layoutPinnedIndicator, tvReplyHeader, messages, listener, currentUserId, serverColor, serverMembers, highlightMessageId, adapter);
            
            if (isFirstInGroup && senderName != null) {
                senderName.setVisibility(View.VISIBLE);
                String uid = message.getSenderId();
                senderName.setTag(uid);
                senderName.setTextColor(getConsistentColor(uid));
                senderName.setOnClickListener(v -> {
                    android.os.Bundle bundle = new android.os.Bundle();
                    bundle.putString("USER_ID", uid);
                    androidx.navigation.Navigation.findNavController(v).navigate(R.id.nav_profile, bundle);
                });

                ServerMember foundMember = null;
                if (serverMembers != null) {
                    for (ServerMember m : serverMembers) {
                        if (m.getUserId() != null && m.getUserId().equals(uid)) {
                            foundMember = m;
                            break;
                        }
                    }
                }

                if (foundMember != null) {
                    String displayName = foundMember.getNickname();
                    if (displayName == null || displayName.trim().isEmpty()) {
                        displayName = foundMember.getUserName();
                    }
                    
                    String cachedPlan = adapter.planCache.get(uid);
                    if (cachedPlan != null) {
                        if ("Pro".equals(cachedPlan)) senderName.setText(displayName + " ✨");
                        else if ("Standard".equals(cachedPlan)) senderName.setText(displayName + " ⭐");
                        else senderName.setText(displayName);
                    } else {
                        senderName.setText(displayName);
                        final String finalName = displayName;
                        db.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                String plan = documentSnapshot.getString("plan");
                                if (plan == null) plan = "Basic";
                                adapter.planCache.put(uid, plan);
                                if (uid.equals(senderName.getTag())) {
                                    if ("Pro".equals(plan)) senderName.setText(finalName + " ✨");
                                    else if ("Standard".equals(plan)) senderName.setText(finalName + " ⭐");
                                }
                            }
                        });
                    }
                } else {
                    db.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && uid.equals(senderName.getTag())) {
                            String displayName = documentSnapshot.getString("username");
                            String plan = documentSnapshot.getString("plan");
                            if (plan == null) plan = "Basic";
                            adapter.planCache.put(uid, plan);
                            
                            if ("Pro".equals(plan)) senderName.setText(displayName + " ✨");
                            else if ("Standard".equals(plan)) senderName.setText(displayName + " ⭐");
                            else senderName.setText(displayName);
                        }
                    });
                }
            } else if (senderName != null) {
                senderName.setVisibility(View.GONE);
            }

            // Xử lý Giờ & Avatar (Hiện ở tin cuối nhóm)
            if (isLastInGroup) {
                if (textTime != null) {
                    textTime.setVisibility(View.VISIBLE);
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    textTime.setText(sdf.format(new Date(message.getTimestamp())));
                }
                if (avatarImg != null) {
                    avatarImg.setVisibility(View.VISIBLE);
                    String uid = message.getSenderId();
                    avatarImg.setTag(uid);
                    avatarImg.setOnClickListener(v -> {
                        android.os.Bundle bundle = new android.os.Bundle();
                        bundle.putString("USER_ID", uid);
                        androidx.navigation.Navigation.findNavController(v).navigate(R.id.nav_profile, bundle);
                    });
                    
                    String cachedPlan = adapter.planCache.get(uid);
                    if (cachedPlan != null) applyAvatarBorder(avatarImg, cachedPlan);
                    else applyAvatarBorder(avatarImg, "Basic");
                    
                    String cachedAvatar = avatarCache.get(uid);
                    if (cachedAvatar != null) {
                        if (!cachedAvatar.isEmpty()) {
                            Glide.with(avatarImg.getContext())
                                 .load(cachedAvatar)
                                 .placeholder(R.drawable.ic_user)
                                 .diskCacheStrategy(DiskCacheStrategy.ALL)
                                 .into(avatarImg);
                        } else {
                            avatarImg.setImageResource(R.drawable.ic_user);
                        }
                    } else {
                        avatarImg.setImageResource(R.drawable.ic_user);
                        db.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                String plan = documentSnapshot.getString("plan");
                                if (plan == null) plan = "Basic";
                                adapter.planCache.put(uid, plan);
                                if (uid.equals(avatarImg.getTag())) applyAvatarBorder(avatarImg, plan);

                                if (uid.equals(avatarImg.getTag())) {
                                    String profilePic = documentSnapshot.getString("profilePic");
                                    if (profilePic == null) profilePic = "";
                                    avatarCache.put(uid, profilePic);
                                    if (!profilePic.isEmpty()) {
                                        Glide.with(avatarImg.getContext())
                                             .load(profilePic)
                                             .placeholder(R.drawable.ic_user)
                                             .diskCacheStrategy(DiskCacheStrategy.ALL)
                                             .into(avatarImg);
                                    }
                                }
                            }
                        });
                    }
                }
            } else {
                if (textTime != null) textTime.setVisibility(View.GONE);
                if (avatarImg != null) avatarImg.setVisibility(View.INVISIBLE);
            }
        }

        private int getConsistentColor(String uid) {
            int hash = uid.hashCode();
            int[] colors = {
                Color.parseColor("#6C63FF"), // Indigo
                Color.parseColor("#FF6B6B"), // Coral
                Color.parseColor("#51CF66"), // Green
                Color.parseColor("#FF922B"), // Orange
                Color.parseColor("#CC5DE8"), // Purple
                Color.parseColor("#22B8CF"), // Teal
                Color.parseColor("#FF6B9D"), // Pink
            };
            return colors[Math.abs(hash) % colors.length];
        }
    }

    private static void bindSharedLogic(Message msg, TextView textMessage, ImageView ivMessageImage, View layoutVideo, ImageView ivVideoThumbnail, LinearLayout layoutFile, TextView tvFileName, LinearLayout layoutAudio, ImageView btnPlayPause, LinearLayout layoutWaveform, TextView tvAudioTime, TextView textReaction, View layoutRepliedContainer, TextView textRepliedTo, ImageView ivRepliedImage, View cardBubble, View layoutPinnedIndicator, TextView tvReplyHeader, List<Message> messages, OnChatInteractListener listener, String currentUserId, String serverColor, List<ServerMember> serverMembers, String highlightMessageId, ChatAdapter adapter) {
        Context ctx = textMessage.getContext();
        Typeface interTypeface = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.inter);
        boolean isSentByMe = msg.getSenderId() != null && msg.getSenderId().equals(currentUserId);
        boolean isHighlighted = msg.getMessageId() != null && msg.getMessageId().equals(highlightMessageId);
        boolean hasReply = msg.getRepliedToContent() != null && !msg.getRepliedToContent().isEmpty() && !msg.isDeleted();

        float density = ctx.getResources().getDisplayMetrics().density;
        int defaultPaddingH = (int) (16 * density);
        int defaultPaddingV = (int) (12 * density);

        // Dynamic background shape based on reply status to connect bubbles visually
        if (isSentByMe && cardBubble instanceof androidx.cardview.widget.CardView) {
            View innerLayout = ((androidx.cardview.widget.CardView) cardBubble).getChildAt(0);
            if (innerLayout != null) {
                if ("audio".equals(msg.getType())) {
                    innerLayout.setBackgroundResource(R.drawable.bg_audio_bubble_sent);
                    innerLayout.setPadding(0, 0, 0, 0);
                } else {
                    innerLayout.setPadding(defaultPaddingH, defaultPaddingV, defaultPaddingH, defaultPaddingV);
                    if (hasReply) {
                        innerLayout.setBackgroundResource(R.drawable.border_chat_reply_sent);
                    } else {
                        innerLayout.setBackgroundResource(R.drawable.border_chat);
                    }
                }
            }
        } else if (!isSentByMe) {
            if ("audio".equals(msg.getType())) {
                cardBubble.setBackgroundResource(R.drawable.bg_audio_bubble_received);
                cardBubble.setPadding(0, 0, 0, 0);
            } else {
                cardBubble.setPadding(defaultPaddingH, defaultPaddingV, defaultPaddingH, defaultPaddingV);
                if (hasReply) {
                    cardBubble.setBackgroundResource(R.drawable.bg_chat_left_reply_received);
                } else {
                    cardBubble.setBackgroundResource(R.drawable.bg_chat_left);
                }
            }
        }

        // Apply overlapping negative margin when reply bubble is present to superimpose bubbles
        if (cardBubble.getLayoutParams() instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = 
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) cardBubble.getLayoutParams();
            if (hasReply) {
                lp.topMargin = (int) (-12 * density); // Overlap bottom of replied bubble by 12dp
            } else {
                lp.topMargin = (int) (4 * density);  // Standard margin
            }
            cardBubble.setLayoutParams(lp);
        }
        if (msg.isPending()) {
            cardBubble.setAlpha(0.5f);
        } else {
            cardBubble.setAlpha(1.0f);
        }

        if (isHighlighted) {
            cardBubble.animate().scaleX(1.08f).scaleY(1.08f).setDuration(250).start();
            try {
                int highlightColor = Color.parseColor("#FFE082"); // Glowing Amber
                if (isSentByMe && cardBubble instanceof androidx.cardview.widget.CardView) {
                    View innerLayout = ((androidx.cardview.widget.CardView) cardBubble).getChildAt(0);
                    if (innerLayout != null && innerLayout.getBackground() != null) {
                        innerLayout.getBackground().mutate().setTint(highlightColor);
                    }
                } else {
                    if (cardBubble.getBackground() != null) {
                        cardBubble.getBackground().mutate().setTint(highlightColor);
                    }
                }
            } catch (Exception e) {}
        } else {
            cardBubble.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
            if (isSentByMe && cardBubble instanceof androidx.cardview.widget.CardView) {
                try {
                    int color = android.graphics.Color.parseColor(serverColor);
                    View innerLayout = ((androidx.cardview.widget.CardView) cardBubble).getChildAt(0);
                    if (innerLayout != null && innerLayout.getBackground() != null) {
                        innerLayout.getBackground().mutate().setTint(color);
                    }
                } catch (Exception e) {}
            } else {
                if (cardBubble.getBackground() != null) {
                    cardBubble.getBackground().mutate().setTintList(null); // Resets tint
                }
            }
        }

        if (layoutPinnedIndicator != null) {
            if (msg.isPinned() && !msg.isDeleted()) {
                layoutPinnedIndicator.setVisibility(View.VISIBLE);
            } else {
                layoutPinnedIndicator.setVisibility(View.GONE);
            }
        }

        if (layoutVideo != null) {
            layoutVideo.setVisibility(View.GONE);
        }

        if (msg.isDeleted()) {
            textMessage.setVisibility(View.VISIBLE);
            textMessage.setText("Tin nhắn đã bị thu hồi");
            textMessage.setTypeface(interTypeface, Typeface.ITALIC);
            // Sent bubble has accent bg → use semi-transparent white
            // Received bubble has theme bg → use text_secondary
            if (isSentByMe) {
                textMessage.setTextColor(Color.argb(180, 255, 255, 255)); // #B4FFFFFF
            } else {
                textMessage.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            }
            if (ivMessageImage != null) ivMessageImage.setVisibility(View.GONE);
            if (layoutFile != null) layoutFile.setVisibility(View.GONE);
            if (layoutAudio != null) layoutAudio.setVisibility(View.GONE);
            if (textReaction != null) textReaction.setVisibility(View.GONE);
            if (textRepliedTo != null) textRepliedTo.setVisibility(View.GONE);
            if (ivRepliedImage != null) ivRepliedImage.setVisibility(View.GONE);
            if (layoutRepliedContainer != null) layoutRepliedContainer.setVisibility(View.GONE);
            if (tvReplyHeader != null) tvReplyHeader.setVisibility(View.GONE);
        } else {
            textMessage.setTypeface(interTypeface, Typeface.NORMAL);
            // Text color is set by layout XML (bubble_text_sent / bubble_text_received)

            // XỬ LÝ PHÂN LOẠI TIN NHẮN (TEXT vs IMAGE vs FILE)
            if ("image".equals(msg.getType())) {
                textMessage.setVisibility(View.GONE);
                if (layoutFile != null) layoutFile.setVisibility(View.GONE);
                if (layoutAudio != null) layoutAudio.setVisibility(View.GONE);

                if (ivMessageImage != null) {
                    ivMessageImage.setVisibility(View.VISIBLE);
                    Glide.with(ivMessageImage.getContext())
                            .load(msg.getContent())
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .apply(RequestOptions.bitmapTransform(new RoundedCorners(32)))
                            .into(ivMessageImage);

                    GestureDetector gestureDetector = new GestureDetector(ivMessageImage.getContext(), new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(MotionEvent e) {
                            return true;
                        }

                        @Override
                        public boolean onSingleTapConfirmed(MotionEvent e) {
                            android.os.Bundle bundle = new android.os.Bundle();
                            bundle.putString("IMAGE_URL", msg.getContent());
                            androidx.navigation.Navigation.findNavController(ivMessageImage).navigate(R.id.nav_core_image_viewer, bundle);
                            return true;
                        }

                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            if ("❤️".equals(msg.getReactionEmoji())) {
                                listener.onReact(msg, "");
                            } else {
                                listener.onReact(msg, "❤️");
                            }
                            return true;
                        }

                        @Override
                        public void onLongPress(MotionEvent e) {
                            cardBubble.performLongClick();
                        }
                    });

                    ivMessageImage.setOnTouchListener((v, event) -> {
                        return gestureDetector.onTouchEvent(event);
                    });
                }
            } else if ("file".equals(msg.getType())) {
                textMessage.setVisibility(View.GONE);
                if (ivMessageImage != null) ivMessageImage.setVisibility(View.GONE);
                if (layoutAudio != null) layoutAudio.setVisibility(View.GONE);

                if (layoutFile != null) {
                    layoutFile.setVisibility(View.VISIBLE);

                    // Trích xuất tên file từ URL của Cloudinary (Thêm final để dùng trong GestureDetector)
                    final String fileUrl = msg.getContent();
                    String extractedFileName = "Tài liệu đính kèm";
                    try {
                        extractedFileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
                    } catch (Exception e) {}
                    final String fileName = extractedFileName;

                    if (tvFileName != null) {
                        tvFileName.setText(fileName);
                    }

                    GestureDetector gestureDetector = new GestureDetector(layoutFile.getContext(), new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(MotionEvent e) {
                            return true;
                        }

                        @Override
                        public boolean onSingleTapConfirmed(MotionEvent e) {
                            // Mở trình xem trước tài liệu thay vì tải ngay
                            android.os.Bundle bundle = new android.os.Bundle();
                            bundle.putString("DOC_URL", fileUrl);
                            bundle.putString("FILE_NAME", fileName);
                            androidx.navigation.Navigation.findNavController(layoutFile).navigate(R.id.nav_core_document_viewer, bundle);
                            return true;
                        }

                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            if ("❤️".equals(msg.getReactionEmoji())) {
                                listener.onReact(msg, "");
                            } else {
                                listener.onReact(msg, "❤️");
                            }
                            return true;
                        }

                        @Override
                        public void onLongPress(MotionEvent e) {
                            cardBubble.performLongClick();
                        }
                    });

                    layoutFile.setOnTouchListener((v, event) -> {
                        return gestureDetector.onTouchEvent(event);
                    });
                }
            } else if ("post_share".equals(msg.getType())) {
                textMessage.setVisibility(View.VISIBLE);
                textMessage.setText("📰 Đã chia sẻ một bài viết\n(Chạm để xem chi tiết)");
                textMessage.setTextColor(Color.parseColor("#5865F2"));
                textMessage.setTypeface(interTypeface, Typeface.BOLD_ITALIC);
                if (ivMessageImage != null) ivMessageImage.setVisibility(View.GONE);
                if (layoutFile != null) layoutFile.setVisibility(View.GONE);
                if (layoutAudio != null) layoutAudio.setVisibility(View.GONE);
                
                cardBubble.setOnClickListener(v -> {
                    String postId = msg.getContent();
                    // Fetch Post to get serverId, channelId
                    FirebaseFirestore.getInstance().collection("Posts").document(postId).get().addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String cId = doc.getString("channelId");
                            String sId = doc.getString("serverId");
                            if (cId != null && sId != null) {
                                android.os.Bundle bundle = new android.os.Bundle();
                                bundle.putString("CHANNEL_ID", cId);
                                bundle.putString("SERVER_ID", sId);
                                bundle.putString("SERVER_COLOR", serverColor);
                                bundle.putString("CHANNEL_NAME", "Bài Viết");
                                androidx.navigation.Navigation.findNavController(cardBubble).navigate(R.id.nav_post_channel, bundle);
                            }
                        } else {
                            android.widget.Toast.makeText(ctx, "Bài viết đã bị xóa", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            } else if ("video".equals(msg.getType())) {
                textMessage.setVisibility(View.GONE);
                if (ivMessageImage != null) ivMessageImage.setVisibility(View.GONE);
                if (layoutFile != null) layoutFile.setVisibility(View.GONE);
                if (layoutAudio != null) layoutAudio.setVisibility(View.GONE);

                if (layoutVideo != null && ivVideoThumbnail != null) {
                    layoutVideo.setVisibility(View.VISIBLE);
                    Glide.with(ivVideoThumbnail.getContext())
                            .load(msg.getContent())
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .apply(RequestOptions.bitmapTransform(new RoundedCorners(32)))
                            .into(ivVideoThumbnail);

                    layoutVideo.setOnClickListener(v -> {
                        android.os.Bundle bundle = new android.os.Bundle();
                        bundle.putString("VIDEO_URL", msg.getContent());
                        androidx.navigation.Navigation.findNavController(layoutVideo).navigate(R.id.nav_core_video_viewer, bundle);
                    });

                    layoutVideo.setOnLongClickListener(v -> {
                        cardBubble.performLongClick();
                        return true;
                    });
                }
            } else if ("audio".equals(msg.getType())) {
                textMessage.setVisibility(View.GONE);
                if (ivMessageImage != null) ivMessageImage.setVisibility(View.GONE);
                if (layoutFile != null) layoutFile.setVisibility(View.GONE);

                if (layoutAudio != null) {
                    layoutAudio.setVisibility(View.VISIBLE);
                    final String audioUrl = msg.getContent();

                    if (tvAudioTime != null) {
                        tvAudioTime.setTag(msg.getMessageId());
                    }

                    String durationStr = msg.getFileUrl();
                    String tempDuration = "00:00";
                    if (durationStr != null && !durationStr.isEmpty()) {
                        try {
                            long durationMs = Long.parseLong(durationStr);
                            tempDuration = formatTime((int) durationMs);
                        } catch (Exception e) {
                            tempDuration = "00:00";
                        }
                    } else {
                        String cached = audioDurationCache.get(audioUrl);
                        if (cached != null) {
                            tempDuration = cached;
                        } else {
                            final String finalAudioUrl = audioUrl;
                            final TextView finalTvAudioTime = tvAudioTime;
                            final String finalMsgId = msg.getMessageId();
                            new Thread(() -> {
                                android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                                try {
                                    retriever.setDataSource(finalAudioUrl, new java.util.HashMap<String, String>());
                                    String time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                                    if (time != null) {
                                        long timeMs = Long.parseLong(time);
                                        String formatted = formatTime((int) timeMs);
                                        audioDurationCache.put(finalAudioUrl, formatted);
                                        if (finalTvAudioTime != null) {
                                            finalTvAudioTime.post(() -> {
                                                if (finalMsgId.equals(finalTvAudioTime.getTag())) {
                                                    boolean currentlyActive = finalAudioUrl.equals(com.example.se114_callingsystem.core.util.AudioPlayerManager.getCurrentAudioUrl());
                                                    if (!currentlyActive) {
                                                        finalTvAudioTime.setText(formatted);
                                                    }
                                                }
                                            });
                                        }
                                    }
                                } catch (Exception e) {
                                    // Log error silently, fallback to 00:00
                                } finally {
                                    try { retriever.release(); } catch (Exception ignored) {}
                                }
                            }).start();
                        }
                    }
                    final String displayDuration = tempDuration;

                    final int activeColor = isSentByMe ? Color.WHITE : Color.parseColor(serverColor);
                    final int inactiveColor = isSentByMe ? Color.parseColor("#40FFFFFF") : Color.parseColor("#B9BBBE");

                    if (layoutWaveform != null) {
                        int[] heights = {8, 12, 16, 24, 18, 12, 8, 10, 16, 22, 28, 20, 14, 12, 18, 24, 16, 10, 8, 12, 18, 14, 8, 6, 4};
                        if (layoutWaveform.getChildCount() == 0) {
                            for (int h : heights) {
                                View bar = new View(ctx);
                                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                    (int) (2.5f * density), // width
                                    (int) (h * density)     // height
                                );
                                lp.setMargins((int) (1.5f * density), 0, (int) (1.5f * density), 0);
                                bar.setLayoutParams(lp);
                                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                                gd.setCornerRadius(100);
                                bar.setBackground(gd);
                                layoutWaveform.addView(bar);
                            }
                        }
                    }

                    boolean isPlaying = com.example.se114_callingsystem.core.util.AudioPlayerManager.isPlaying(audioUrl);
                    boolean isActive = audioUrl.equals(com.example.se114_callingsystem.core.util.AudioPlayerManager.getCurrentAudioUrl());

                    if (isActive) {
                        if (btnPlayPause != null) {
                            btnPlayPause.setImageResource(R.drawable.ic_pause);
                        }
                        if (layoutWaveform != null && tvAudioTime != null) {
                            Runnable oldRunnable = (Runnable) layoutWaveform.getTag();
                            if (oldRunnable != null) {
                                layoutWaveform.removeCallbacks(oldRunnable);
                            }
                            Runnable updateProgressRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    if (audioUrl.equals(com.example.se114_callingsystem.core.util.AudioPlayerManager.getCurrentAudioUrl())) {
                                        boolean playing = com.example.se114_callingsystem.core.util.AudioPlayerManager.isPlaying(audioUrl);
                                        int current = 0;
                                        int duration = 0;
                                        if (playing) {
                                            current = com.example.se114_callingsystem.core.util.AudioPlayerManager.getCurrentPosition();
                                            duration = com.example.se114_callingsystem.core.util.AudioPlayerManager.getDuration();
                                        }
                                        
                                        float percent = 0f;
                                        if (duration > 0) {
                                            percent = (float) current / duration;
                                            tvAudioTime.setText(formatTime(current));
                                        } else {
                                            tvAudioTime.setText("00:00");
                                        }

                                        int barCount = layoutWaveform.getChildCount();
                                        int activeCount = (int) (percent * barCount);
                                        for (int i = 0; i < barCount; i++) {
                                            View bar = layoutWaveform.getChildAt(i);
                                            android.graphics.drawable.Drawable bg = bar.getBackground();
                                            if (bg instanceof android.graphics.drawable.GradientDrawable) {
                                                ((android.graphics.drawable.GradientDrawable) bg).setColor(
                                                    i < activeCount ? activeColor : inactiveColor
                                                );
                                            }
                                        }

                                        layoutWaveform.postDelayed(this, 100);
                                    } else {
                                        if (btnPlayPause != null) {
                                            btnPlayPause.setImageResource(R.drawable.ic_play);
                                        }
                                        int barCount = layoutWaveform.getChildCount();
                                        for (int i = 0; i < barCount; i++) {
                                            View bar = layoutWaveform.getChildAt(i);
                                            android.graphics.drawable.Drawable bg = bar.getBackground();
                                            if (bg instanceof android.graphics.drawable.GradientDrawable) {
                                                ((android.graphics.drawable.GradientDrawable) bg).setColor(inactiveColor);
                                            }
                                        }
                                        tvAudioTime.setText(displayDuration);
                                    }
                                }
                            };
                            layoutWaveform.setTag(updateProgressRunnable);
                            layoutWaveform.post(updateProgressRunnable);
                        }
                    } else {
                        if (btnPlayPause != null) {
                            btnPlayPause.setImageResource(R.drawable.ic_play);
                        }
                        if (layoutWaveform != null) {
                            Runnable oldRunnable = (Runnable) layoutWaveform.getTag();
                            if (oldRunnable != null) {
                                layoutWaveform.removeCallbacks(oldRunnable);
                                layoutWaveform.setTag(null);
                            }
                            int barCount = layoutWaveform.getChildCount();
                            for (int i = 0; i < barCount; i++) {
                                View bar = layoutWaveform.getChildAt(i);
                                android.graphics.drawable.Drawable bg = bar.getBackground();
                                if (bg instanceof android.graphics.drawable.GradientDrawable) {
                                    ((android.graphics.drawable.GradientDrawable) bg).setColor(inactiveColor);
                                }
                            }
                        }
                        if (tvAudioTime != null) {
                            tvAudioTime.setText(displayDuration);
                        }
                    }

                    if (layoutWaveform != null) {
                        layoutWaveform.setOnTouchListener((v, event) -> {
                            if (com.example.se114_callingsystem.core.util.AudioPlayerManager.isPlaying(audioUrl)) {
                                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                                    float width = v.getWidth();
                                    float x = event.getX();
                                    float percent = Math.max(0f, Math.min(1f, x / width));
                                    int duration = com.example.se114_callingsystem.core.util.AudioPlayerManager.getDuration();
                                    if (duration > 0) {
                                        com.example.se114_callingsystem.core.util.AudioPlayerManager.seekTo((int) (percent * duration));
                                    }
                                }
                                return true;
                            }
                            return false;
                        });
                    }

                    View.OnClickListener playPauseClick = v -> {
                        boolean currentlyActive = audioUrl.equals(com.example.se114_callingsystem.core.util.AudioPlayerManager.getCurrentAudioUrl());
                        if (currentlyActive) {
                            com.example.se114_callingsystem.core.util.AudioPlayerManager.stop();
                            adapter.notifyDataSetChanged();
                        } else {
                            com.example.se114_callingsystem.core.util.AudioPlayerManager.play(audioUrl, new com.example.se114_callingsystem.core.util.AudioPlayerManager.AudioPlayerListener() {
                                @Override
                                public void onStart() {
                                    adapter.notifyDataSetChanged();
                                }

                                @Override
                                public void onStop() {
                                    adapter.notifyDataSetChanged();
                                }

                                @Override
                                public void onComplete() {
                                    adapter.notifyDataSetChanged();
                                }

                                @Override
                                public void onError(String error) {
                                    android.widget.Toast.makeText(ctx, "Lỗi phát âm thanh: " + error, android.widget.Toast.LENGTH_SHORT).show();
                                    adapter.notifyDataSetChanged();
                                }
                            });
                            adapter.notifyDataSetChanged();
                        }
                    };

                    if (btnPlayPause != null) {
                        btnPlayPause.setOnClickListener(playPauseClick);
                    }
                    layoutAudio.setOnClickListener(playPauseClick);
                    layoutAudio.setOnLongClickListener(v -> {
                        cardBubble.performLongClick();
                        return true;
                    });
                }
            } else {
                // Tin nhắn văn bản bình thường
                textMessage.setVisibility(View.VISIBLE);
                
                try {
                    io.noties.markwon.Markwon.create(ctx).setMarkdown(textMessage, msg.getContent());
                } catch (Exception e) {
                    textMessage.setText(msg.getContent());
                }
                
                // Tự động nhận diện URL, gạch chân và cho phép click
                android.text.util.Linkify.addLinks(textMessage, android.text.util.Linkify.WEB_URLS);

                highlightMentionsInSpannable(textMessage, serverColor, serverMembers, isSentByMe);
                
                // Đổi màu link để dễ đọc trên các nền bubble khác nhau
                if (isSentByMe) {
                    // Bubble của mình màu tím -> link màu trắng cho dễ nhìn
                    textMessage.setLinkTextColor(android.graphics.Color.WHITE);
                } else {
                    // Bubble người khác -> dùng màu accent (xanh tím) cho nổi bật
                    textMessage.setLinkTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.accent));
                }

                if (ivMessageImage != null) {
                    ivMessageImage.setVisibility(View.GONE);
                }
                if (layoutFile != null) {
                    layoutFile.setVisibility(View.GONE);
                }
                if (layoutAudio != null) {
                    layoutAudio.setVisibility(View.GONE);
                }
            }

            // Xử lý Reaction Indicator
            if (textReaction != null) {
                if (msg.getReactionEmoji() != null && !msg.getReactionEmoji().isEmpty()) {
                    textReaction.setText(msg.getReactionEmoji());
                    textReaction.setVisibility(View.VISIBLE);
                } else {
                    textReaction.setVisibility(View.GONE);
                }
            }

            // Xử lý Reply Indicator - hỗ trợ hiển ảnh khi reply tin nhắn ảnh và các kết nối
            if (layoutRepliedContainer != null) {
                if (msg.getRepliedToContent() != null && !msg.getRepliedToContent().isEmpty()) {
                    layoutRepliedContainer.setVisibility(View.VISIBLE);
                    
                    String repliedType = msg.getRepliedToType();
                    String replyContent = msg.getRepliedToContent();

                    if ("image".equals(repliedType)) {
                        if (textRepliedTo != null) {
                            textRepliedTo.setText("📷 Hình ảnh");
                            textRepliedTo.setVisibility(View.VISIBLE);
                        }
                        if (ivRepliedImage != null) {
                            ivRepliedImage.setVisibility(View.VISIBLE);
                             Glide.with(ivRepliedImage.getContext())
                                     .load(replyContent)
                                     .diskCacheStrategy(DiskCacheStrategy.ALL)
                                     .apply(RequestOptions.bitmapTransform(new RoundedCorners(16)))
                                     .into(ivRepliedImage);
                        }
                    } else if ("video".equals(repliedType)) {
                        if (textRepliedTo != null) {
                            textRepliedTo.setText("🎥 Video");
                            textRepliedTo.setVisibility(View.VISIBLE);
                        }
                        if (ivRepliedImage != null) {
                            ivRepliedImage.setVisibility(View.VISIBLE);
                             Glide.with(ivRepliedImage.getContext())
                                     .load(replyContent)
                                     .diskCacheStrategy(DiskCacheStrategy.ALL)
                                     .apply(RequestOptions.bitmapTransform(new RoundedCorners(16)))
                                     .into(ivRepliedImage);
                        }
                    } else if ("file".equals(repliedType)) {
                        // Reply to file - show file name with icon
                        String fileName = "Tài liệu đính kèm";
                        try {
                            fileName = replyContent.substring(replyContent.lastIndexOf('/') + 1);
                        } catch (Exception e) {}
                        if (textRepliedTo != null) {
                            textRepliedTo.setText("📎 " + fileName);
                            textRepliedTo.setVisibility(View.VISIBLE);
                        }
                        if (ivRepliedImage != null) ivRepliedImage.setVisibility(View.GONE);
                    } else {
                        // Reply to text
                        String previewContent = replyContent;
                        if (previewContent.length() > 30) previewContent = previewContent.substring(0, 30) + "...";
                        if (textRepliedTo != null) {
                            textRepliedTo.setText(previewContent);
                            textRepliedTo.setVisibility(View.VISIBLE);
                        }
                        if (ivRepliedImage != null) ivRepliedImage.setVisibility(View.GONE);
                    }

                    // Tìm người gửi tin nhắn được trả lời để hiển thị header
                    String repliedToSenderId = null;
                    if (messages != null && msg.getRepliedToMessageId() != null && !msg.getRepliedToMessageId().isEmpty()) {
                        for (Message m : messages) {
                            if (msg.getRepliedToMessageId().equals(m.getMessageId())) {
                                repliedToSenderId = m.getSenderId();
                                break;
                            }
                        }
                    }
                    updateReplyHeader(msg.getSenderId(), repliedToSenderId, tvReplyHeader, currentUserId, serverMembers);

                    layoutRepliedContainer.setOnClickListener(v -> {
                        listener.onRepliedMessageClick(msg);
                    });
                } else {
                    layoutRepliedContainer.setVisibility(View.GONE);
                    if (tvReplyHeader != null) tvReplyHeader.setVisibility(View.GONE);
                }
            } else {
                if (textRepliedTo != null) textRepliedTo.setVisibility(View.GONE);
                if (ivRepliedImage != null) ivRepliedImage.setVisibility(View.GONE);
                if (tvReplyHeader != null) tvReplyHeader.setVisibility(View.GONE);
            }
        }


        // --- XỬ LÝ SỰ KIỆN CLICK VÀ LONG CLICK CHO BONG BÓNG CHAT ---
        if (cardBubble != null) {
            final long[] lastClickTime = {0};
            if (!"post_share".equals(msg.getType())) {
                cardBubble.setOnClickListener(v -> {
                    if (msg.isDeleted()) return;
                    long clickTime = System.currentTimeMillis();
                    if (clickTime - lastClickTime[0] < 300) {
                        // Double Click để thả tim cho văn bản
                        if ("❤️".equals(msg.getReactionEmoji())) listener.onReact(msg, "");
                        else listener.onReact(msg, "❤️");
                    }
                    lastClickTime[0] = clickTime;
                });
            }

            cardBubble.setOnLongClickListener(v -> {
                if (!msg.isDeleted()) {
                    BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(v.getContext());
                    View sheetView = LayoutInflater.from(v.getContext()).inflate(R.layout.layout_chat_bottom_sheet_menu, null);
                    bottomSheetDialog.setContentView(sheetView);

                    try { ((View) sheetView.getParent()).setBackgroundColor(Color.TRANSPARENT); } catch (Exception e) {}

                    TextView btnDelete = sheetView.findViewById(R.id.btnDelete);
                    TextView btnRemoveReaction = sheetView.findViewById(R.id.btnRemoveReaction);
                    TextView btnPin = sheetView.findViewById(R.id.btnPin);

                    btnDelete.setVisibility(msg.getSenderId().equals(currentUserId) ? View.VISIBLE : View.GONE);
                    btnRemoveReaction.setVisibility((msg.getReactionEmoji() != null && !msg.getReactionEmoji().isEmpty()) ? View.VISIBLE : View.GONE);

                    View dividerAction = sheetView.findViewById(R.id.dividerAction);
                    if (dividerAction != null) {
                        dividerAction.setVisibility(btnRemoveReaction.getVisibility());
                    }

                    if (msg.isPinned()) {
                        btnPin.setText("Bỏ ghim tin nhắn");
                    } else {
                        btnPin.setText("Ghim tin nhắn");
                    }
                    btnPin.setOnClickListener(view -> {
                        listener.onPinToggle(msg);
                        bottomSheetDialog.dismiss();
                    });

                    TextView btnSetReminder = sheetView.findViewById(R.id.btnSetReminder);
                    if (btnSetReminder != null) {
                        if ("reminder".equals(msg.getType())) {
                            btnSetReminder.setVisibility(View.GONE);
                        } else {
                            btnSetReminder.setVisibility(View.VISIBLE);
                            btnSetReminder.setOnClickListener(view -> {
                                listener.onEditReminder(msg);
                                bottomSheetDialog.dismiss();
                            });
                        }
                    }

                    sheetView.findViewById(R.id.btnReactLike).setOnClickListener(view -> { listener.onReact(msg, "👍"); bottomSheetDialog.dismiss(); });
                    sheetView.findViewById(R.id.btnReactLove).setOnClickListener(view -> { listener.onReact(msg, "❤️"); bottomSheetDialog.dismiss(); });
                    sheetView.findViewById(R.id.btnReactHaha).setOnClickListener(view -> { listener.onReact(msg, "😂"); bottomSheetDialog.dismiss(); });
                    sheetView.findViewById(R.id.btnReactWow).setOnClickListener(view -> { listener.onReact(msg, "😮"); bottomSheetDialog.dismiss(); });
                    sheetView.findViewById(R.id.btnReactSad).setOnClickListener(view -> { listener.onReact(msg, "😢"); bottomSheetDialog.dismiss(); });
                    sheetView.findViewById(R.id.btnReactAngry).setOnClickListener(view -> { listener.onReact(msg, "😡"); bottomSheetDialog.dismiss(); });
                    btnRemoveReaction.setOnClickListener(view -> { listener.onReact(msg, ""); bottomSheetDialog.dismiss(); });
                    btnDelete.setOnClickListener(view -> { listener.onDelete(msg); bottomSheetDialog.dismiss(); });

                    bottomSheetDialog.show();
                }
                return true;
            });
        }
    }

    private static void highlightMentionsInSpannable(TextView textView, String serverColorStr, List<ServerMember> serverMembers, boolean isSentByMe) {
        if (textView == null) return;
        CharSequence text = textView.getText();
        if (text == null) return;
        
        android.text.SpannableStringBuilder spannable = new android.text.SpannableStringBuilder(text);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("@(\\w+)");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        boolean hasMention = false;

        while (matcher.find()) {
            String candidate = matcher.group(1);
            boolean isMember = false;
            if (serverMembers != null) {
                for (ServerMember m : serverMembers) {
                    if (candidate.equalsIgnoreCase(m.getUserName()) || candidate.equalsIgnoreCase(m.getNickname())) {
                        isMember = true;
                        break;
                    }
                }
            }

            if (isMember) {
                hasMention = true;
                int start = matcher.start();
                int end = matcher.end();

                // Nếu là tin nhắn của mình gửi: Highlight màu vàng
                // Nếu là tin nhắn của người khác gửi: Highlight màu serverColor của server đó
                int highlightColor = Color.parseColor("#FFD700"); // Yellow
                if (!isSentByMe) {
                    try {
                        highlightColor = Color.parseColor(serverColorStr);
                    } catch (Exception e) {}
                }

                // Dùng BackgroundColorSpan để vẽ khung màu nền
                spannable.setSpan(new android.text.style.BackgroundColorSpan(Color.argb(40, Color.red(highlightColor), Color.green(highlightColor), Color.blue(highlightColor))), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                // Dùng ForegroundColorSpan để đổi màu chữ mention
                spannable.setSpan(new android.text.style.ForegroundColorSpan(highlightColor), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                // Dùng StyleSpan để in đậm chữ mention
                spannable.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        if (hasMention) {
            textView.setText(spannable);
        }
    }

    private static void updateReplyHeader(String senderId, String repliedToSenderId, TextView tvReplyHeader, String currentUserId, List<ServerMember> serverMembers) {
        if (tvReplyHeader == null) return;

        Context ctx = tvReplyHeader.getContext();
        String myName = "Bạn";
        String otherName = "Ai đó";

        if (repliedToSenderId == null) {
            tvReplyHeader.setVisibility(View.GONE);
            return;
        }

        // Tự động phân giải hiển thị tên (Bản thân vs Member)
        if (repliedToSenderId.equals(currentUserId)) {
            otherName = myName;
        } else {
            ServerMember found = null;
            if (serverMembers != null) {
                for (ServerMember m : serverMembers) {
                    if (repliedToSenderId.equals(m.getUserId())) { found = m; break; }
                }
            }
            if (found != null) {
                otherName = found.getNickname();
                if (otherName == null || otherName.isEmpty()) otherName = found.getUserName();
            }
        }

        tvReplyHeader.setText(otherName + " đã trả lời");
        tvReplyHeader.setVisibility(View.VISIBLE);
    }

    private static String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
