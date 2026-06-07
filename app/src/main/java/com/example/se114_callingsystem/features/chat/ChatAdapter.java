package com.example.se114_callingsystem.features.chat;

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
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
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
            ((SentMessageViewHolder) holder).bind(message, listener, currentUserId, isLastInGroup, serverColor, serverMembers, highlightMessageId);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(message, isFirstInGroup, isLastInGroup, listener, currentUserId, serverColor, serverMembers, highlightMessageId);
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
            if (message.isDeleted()) {
                tvReminderContent.setText("Lời nhắc đã bị xóa");
                tvReminderContent.setTypeface(null, android.graphics.Typeface.ITALIC);
                tvReminderTime.setVisibility(View.GONE);
                cardReminder.setOnClickListener(null);
                cardReminder.setOnLongClickListener(null);
            } else {
                tvReminderContent.setText(message.getContent());
                tvReminderContent.setTypeface(null, android.graphics.Typeface.NORMAL);
                tvReminderTime.setVisibility(View.VISIBLE);
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                tvReminderTime.setText(sdf.format(new Date(message.getReminderTime())));
                
                cardReminder.setOnLongClickListener(v -> {
                    if (message.getReminderTime() < System.currentTimeMillis()) {
                        android.widget.Toast.makeText(v.getContext(), "Không thể sửa hoặc xóa lời nhắc đã qua", android.widget.Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    String[] options = {"✏️ Sửa lời nhắc", "🗑️ Xóa lời nhắc"};
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(v.getContext())
                            .setTitle("Tùy chọn lời nhắc")
                            .setItems(options, (dialog, which) -> {
                                if (which == 0) {
                                    listener.onEditReminder(message);
                                } else {
                                    listener.onDelete(message);
                                }
                            })
                            .show();
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
        TextView messageText, textReaction, textRepliedTo, textTime, tvFileName;
        ImageView ivMessageImage, ivRepliedImage;
        LinearLayout layoutFile, layoutPinnedIndicator;
        View cardBubble;

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
        }

        void bind(Message message, OnChatInteractListener listener, String currentUserId, boolean isLastInGroup, String serverColor, List<ServerMember> serverMembers, String highlightMessageId) {
            bindSharedLogic(message, messageText, ivMessageImage, layoutFile, tvFileName, textReaction, textRepliedTo, ivRepliedImage, cardBubble, layoutPinnedIndicator, listener, currentUserId, serverColor, serverMembers, highlightMessageId);

            if (isLastInGroup && textTime != null) {
                textTime.setVisibility(View.VISIBLE);
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                textTime.setText(sdf.format(new Date(message.getTimestamp())));
            } else if (textTime != null) {
                textTime.setVisibility(View.GONE);
            }
        }
    }

    public static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, senderName, textTime, textReaction, textRepliedTo, tvFileName;
        ImageView avatarImg, ivMessageImage, ivRepliedImage;
        LinearLayout layoutFile, layoutPinnedIndicator;
        View cardBubble;

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
        }

        void bind(Message message, boolean isFirstInGroup, boolean isLastInGroup, OnChatInteractListener listener, String currentUserId, String serverColor, List<ServerMember> serverMembers, String highlightMessageId) {
            bindSharedLogic(message, messageText, ivMessageImage, layoutFile, tvFileName, textReaction, textRepliedTo, ivRepliedImage, cardBubble, layoutPinnedIndicator, listener, currentUserId, serverColor, serverMembers, highlightMessageId);
            // Xử lý Tên (Hiện ở tin đầu nhóm)
            if (isFirstInGroup && senderName != null) {
                senderName.setVisibility(View.VISIBLE);
                String uid = message.getSenderId();
                senderName.setTag(uid);
                senderName.setTextColor(getConsistentColor(uid));

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
                    senderName.setText(displayName);
                } else {
                    db.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists() && uid.equals(senderName.getTag())) {
                            senderName.setText(documentSnapshot.getString("username"));
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
                if (avatarImg != null) avatarImg.setVisibility(View.VISIBLE);
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

    private static void bindSharedLogic(Message msg, TextView textMessage, ImageView ivMessageImage, LinearLayout layoutFile, TextView tvFileName, TextView textReaction, TextView textRepliedTo, ImageView ivRepliedImage, View cardBubble, View layoutPinnedIndicator, OnChatInteractListener listener, String currentUserId, String serverColor, List<ServerMember> serverMembers, String highlightMessageId) {
        Context ctx = textMessage.getContext();
        boolean isSentByMe = msg.getSenderId() != null && msg.getSenderId().equals(currentUserId);
        boolean isHighlighted = msg.getMessageId() != null && msg.getMessageId().equals(highlightMessageId);

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

        if (msg.isDeleted()) {
            textMessage.setVisibility(View.VISIBLE);
            textMessage.setText("Tin nhắn đã bị thu hồi");
            textMessage.setTypeface(null, Typeface.ITALIC);
            // Sent bubble has accent bg → use semi-transparent white
            // Received bubble has theme bg → use text_secondary
            if (isSentByMe) {
                textMessage.setTextColor(Color.argb(180, 255, 255, 255)); // #B4FFFFFF
            } else {
                textMessage.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            }
            if (ivMessageImage != null) ivMessageImage.setVisibility(View.GONE);
            if (layoutFile != null) layoutFile.setVisibility(View.GONE);
            if (textReaction != null) textReaction.setVisibility(View.GONE);
            if (textRepliedTo != null) textRepliedTo.setVisibility(View.GONE);
            if (ivRepliedImage != null) ivRepliedImage.setVisibility(View.GONE);
        } else {
            textMessage.setTypeface(null, Typeface.NORMAL);
            // Text color is set by layout XML (bubble_text_sent / bubble_text_received)

            // XỬ LÝ PHÂN LOẠI TIN NHẮN (TEXT vs IMAGE vs FILE)
            if ("image".equals(msg.getType())) {
                textMessage.setVisibility(View.GONE);
                if (layoutFile != null) layoutFile.setVisibility(View.GONE);

                if (ivMessageImage != null) {
                    ivMessageImage.setVisibility(View.VISIBLE);
                    Glide.with(ivMessageImage.getContext())
                            .load(msg.getContent())
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
                textMessage.setTypeface(null, Typeface.BOLD_ITALIC);
                if (ivMessageImage != null) ivMessageImage.setVisibility(View.GONE);
                if (layoutFile != null) layoutFile.setVisibility(View.GONE);
                
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
            } else {
                // Tin nhắn văn bản bình thường
                textMessage.setVisibility(View.VISIBLE);
                textMessage.setText(msg.getContent());
                
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

            // Xử lý Reply Indicator - hỗ trợ hiển ảnh khi reply tin nhắn ảnh
            if (textRepliedTo != null) {
                if (msg.getRepliedToContent() != null && !msg.getRepliedToContent().isEmpty()) {
                    String repliedType = msg.getRepliedToType();
                    String replyContent = msg.getRepliedToContent();

                    if ("image".equals(repliedType)) {
                        if(msg.getSenderId().equals(currentUserId))
                        textRepliedTo.setBackgroundColor(Color.parseColor(serverColor));
                        textRepliedTo.setText("Đang trả lời: 📷 Hình ảnh");
                        textRepliedTo.setVisibility(View.VISIBLE);
                        if (ivRepliedImage != null) {
                            ivRepliedImage.setVisibility(View.VISIBLE);
                            Glide.with(ivRepliedImage.getContext())
                                    .load(replyContent)
                                    .apply(RequestOptions.bitmapTransform(new RoundedCorners(16)))
                                    .into(ivRepliedImage);
                        }
                    } else if ("file".equals(repliedType)) {
                        // Reply to file - show file name with icon
                        String fileName = "Tài liệu đính kèm";
                        try {
                            fileName = replyContent.substring(replyContent.lastIndexOf('/') + 1);
                        } catch (Exception e) {}
                        textRepliedTo.setText("Đang trả lời: 📎 " + fileName);
                        textRepliedTo.setVisibility(View.VISIBLE);
                        if (ivRepliedImage != null) ivRepliedImage.setVisibility(View.GONE);
                    } else {
                        // Reply to text
                        if (replyContent.length() > 30) replyContent = replyContent.substring(0, 30) + "...";
                        textRepliedTo.setText("Đang trả lời: " + replyContent);
                        textRepliedTo.setVisibility(View.VISIBLE);
                        if (ivRepliedImage != null) ivRepliedImage.setVisibility(View.GONE);
                    }
                } else {
                    textRepliedTo.setVisibility(View.GONE);
                    if (ivRepliedImage != null) ivRepliedImage.setVisibility(View.GONE);
                }
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
        android.text.SpannableString spannable = new android.text.SpannableString(text);
        String textStr = spannable.toString();

        int highlightColor;
        if (isSentByMe) {
            highlightColor = android.graphics.Color.WHITE;
        } else {
            try {
                highlightColor = android.graphics.Color.parseColor(serverColorStr);
            } catch (Exception e) {
                highlightColor = android.graphics.Color.parseColor("#FF007F");
            }
        }
        final int finalHighlightColor = highlightColor;

        class MemberNameMapping {
            final String name;
            final String userId;
            MemberNameMapping(String name, String userId) {
                this.name = name;
                this.userId = userId;
            }
        }

        List<MemberNameMapping> nameMappings = new java.util.ArrayList<>();
        if (serverMembers != null) {
            for (ServerMember m : serverMembers) {
                if (m.getUserId() == null) continue;
                if (m.getNickname() != null && !m.getNickname().trim().isEmpty()) {
                    nameMappings.add(new MemberNameMapping(m.getNickname(), m.getUserId()));
                }
                if (m.getUserName() != null && !m.getUserName().trim().isEmpty()) {
                    nameMappings.add(new MemberNameMapping(m.getUserName(), m.getUserId()));
                }
            }
        }

        // Sort by name length descending to avoid partial matches
        java.util.Collections.sort(nameMappings, (m1, m2) -> Integer.compare(m2.name.length(), m1.name.length()));

        boolean[] highlighted = new boolean[textStr.length()];

        for (MemberNameMapping mapping : nameMappings) {
            String mentionTag = "@" + mapping.name;
            int index = textStr.indexOf(mentionTag);
            while (index >= 0) {
                int end = index + mentionTag.length();
                boolean alreadyUsed = false;
                for (int i = index; i < end; i++) {
                    if (highlighted[i]) {
                        alreadyUsed = true;
                        break;
                    }
                }

                if (!alreadyUsed) {
                    for (int i = index; i < end; i++) {
                        highlighted[i] = true;
                    }

                    final String targetUserId = mapping.userId;
                    spannable.setSpan(new android.text.style.ClickableSpan() {
                        @Override
                        public void onClick(@NonNull android.view.View widget) {
                            android.os.Bundle bundle = new android.os.Bundle();
                            bundle.putString("USER_ID", targetUserId);
                            androidx.navigation.Navigation.findNavController(widget).navigate(R.id.nav_profile, bundle);
                        }
                        @Override
                        public void updateDrawState(@NonNull android.text.TextPaint ds) {
                            super.updateDrawState(ds);
                            ds.setColor(finalHighlightColor);
                            ds.setUnderlineText(false);
                        }
                    }, index, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                    spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), index, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                index = textStr.indexOf(mentionTag, index + 1);
            }
        }

        // Regex fallback
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("@[A-Za-z0-9_ÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚÝàáâãèéêìíòóôõùúýĂăĐđĨĩŨũƠơƯưẠ-ỹ]+");
        java.util.regex.Matcher matcher = pattern.matcher(textStr);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            boolean alreadyUsed = false;
            for (int i = start; i < end; i++) {
                if (highlighted[i]) {
                    alreadyUsed = true;
                    break;
                }
            }

            if (!alreadyUsed) {
                for (int i = start; i < end; i++) {
                    highlighted[i] = true;
                }
                spannable.setSpan(new android.text.style.ForegroundColorSpan(highlightColor), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        textView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        textView.setHighlightColor(android.graphics.Color.TRANSPARENT);
        textView.setText(spannable);
    }
}

