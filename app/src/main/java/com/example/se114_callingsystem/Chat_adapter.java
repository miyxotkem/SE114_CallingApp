package com.example.se114_callingsystem;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Chat_adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private List<MessageModel> mMessages;
    private static FirebaseFirestore db;
    private String currentUserId = "znNKHjrncFBE39hu8h8V"; // ID của Nhã
    private OnChatInteractListener listener;

    public interface OnChatInteractListener {
        void onReply(MessageModel message);
        void onDelete(MessageModel message);
        void onReact(MessageModel message, String emoji);
    }

    public Chat_adapter(List<MessageModel> messages, OnChatInteractListener listener) {
        this.mMessages = messages;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        if (mMessages.get(position).getSenderId().equals(currentUserId)) {
            return TYPE_SENT;
        } else {
            return TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_item_chat_bubble, parent, false);
            return new SentMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_item_chat_bubble_receive, parent, false);
            return new ReceivedMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageModel message = mMessages.get(position);
        db = FirebaseFirestore.getInstance();

        boolean showName = true;
        if (position > 0) {
            MessageModel previousMsg = mMessages.get(position - 1);
            if (previousMsg.getSenderId().equals(message.getSenderId())) {
                showName = false;
            }
        }

        if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(message, listener, currentUserId);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(message, showName, listener, currentUserId);
        }
    }

    @Override
    public int getItemCount() {
        return mMessages != null ? mMessages.size() : 0;
    }

    // --- VIEWHOLDERS ---

    public static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, textReaction, textRepliedTo;
        View cardBubble;

        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessage);
            textReaction = itemView.findViewById(R.id.textReaction);
            textRepliedTo = itemView.findViewById(R.id.textRepliedTo);
            cardBubble = itemView.findViewById(R.id.cardBubble);
        }

        void bind(MessageModel message, OnChatInteractListener listener, String currentUserId) {
            bindSharedLogic(message, messageText, textReaction, textRepliedTo, cardBubble, listener, currentUserId);
        }
    }

    public static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, senderName, textTime, textReaction, textRepliedTo;
        View cardBubble;

        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.textMessage);
            senderName = itemView.findViewById(R.id.textSenderName);
            textTime = itemView.findViewById(R.id.textTime);
            textReaction = itemView.findViewById(R.id.textReaction);
            textRepliedTo = itemView.findViewById(R.id.textRepliedTo);
            cardBubble = itemView.findViewById(R.id.cardBubble);
        }

        void bind(MessageModel message, boolean showName, OnChatInteractListener listener, String currentUserId) {
            bindSharedLogic(message, messageText, textReaction, textRepliedTo, cardBubble, listener, currentUserId);

            senderName.setVisibility(showName ? View.VISIBLE : View.GONE);
            Date date = new Date(message.getTimestamp());
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            textTime.setText(sdf.format(date));

            if (showName) {
                String uid = message.getSenderId();
                senderName.setTag(uid);
                senderName.setTextColor(getConsistentColor(uid));

                db.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && uid.equals(senderName.getTag())) {
                        String name = documentSnapshot.getString("username");
                        senderName.setText(name != null ? name : "Người dùng");
                    }
                }).addOnFailureListener(e -> {
                    Log.e("Firestore", "Lỗi lấy tên: " + e.getMessage());
                    senderName.setText("Lỗi");
                });
            }
        }

        private int getConsistentColor(String uid) {
            int hash = uid.hashCode();
            int[] colors = {Color.RED, Color.BLUE, Color.parseColor("#FF9800"), Color.parseColor("#4CAF50"), Color.MAGENTA};
            return colors[Math.abs(hash) % colors.length];
        }
    }

    // --- HÀM DÙNG CHUNG (Với logic Double Tap và Menu BottomSheet) ---
    private static void bindSharedLogic(MessageModel msg, TextView textMessage, TextView textReaction, TextView textRepliedTo, View cardBubble, OnChatInteractListener listener, String currentUserId) {

        if (msg.isDeleted()) {
            textMessage.setText("Tin nhắn đã bị thu hồi");
            textMessage.setTypeface(null, Typeface.ITALIC);
            textMessage.setTextColor(Color.GRAY);
            if (textReaction != null) textReaction.setVisibility(View.GONE);
            if (textRepliedTo != null) textRepliedTo.setVisibility(View.GONE);
        } else {
            textMessage.setText(msg.getContent());
            textMessage.setTypeface(null, Typeface.NORMAL);
            textMessage.setTextColor(Color.BLACK);

            if (textReaction != null) {
                if (msg.getReactionEmoji() != null && !msg.getReactionEmoji().isEmpty()) {
                    textReaction.setText(msg.getReactionEmoji());
                    textReaction.setVisibility(View.VISIBLE);
                } else {
                    textReaction.setVisibility(View.GONE);
                }
            }

            if (textRepliedTo != null) {
                if (msg.getRepliedToContent() != null && !msg.getRepliedToContent().isEmpty()) {
                    String replyContent = msg.getRepliedToContent();
                    if (replyContent.length() > 30) {
                        replyContent = replyContent.substring(0, 30) + "...";
                    }
                    textRepliedTo.setText("Đang trả lời: " + replyContent);
                    textRepliedTo.setVisibility(View.VISIBLE);
                } else {
                    textRepliedTo.setVisibility(View.GONE);
                }
            }
        }

        if (cardBubble != null) {

            // --- LOGIC DOUBLE TAP ĐỂ THẢ TIM ---
            final long[] lastClickTime = {0};
            cardBubble.setOnClickListener(v -> {
                if (msg.isDeleted()) return;

                long clickTime = System.currentTimeMillis();
                // Khoảng cách giữa 2 lần nhấn dưới 300ms được tính là Double Tap
                if (clickTime - lastClickTime[0] < 300) {
                    // Nếu đã thả tim rồi thì gỡ (truyền chuỗi rỗng), chưa thì thả
                    if ("❤️".equals(msg.getReactionEmoji())) {
                        listener.onReact(msg, "");
                    } else {
                        listener.onReact(msg, "❤️");
                    }
                }
                lastClickTime[0] = clickTime;
            });

            // --- MENU NHẤN GIỮ KHI LONG CLICK ---
            cardBubble.setOnLongClickListener(v -> {
                if (!msg.isDeleted()) {
                    BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(v.getContext());

                    View sheetView = LayoutInflater.from(v.getContext()).inflate(R.layout.layout_bottom_sheet_menu, null);
                    bottomSheetDialog.setContentView(sheetView);

                    try {
                        ((View) sheetView.getParent()).setBackgroundColor(Color.TRANSPARENT);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // Tìm các nút
                    TextView btnDelete = sheetView.findViewById(R.id.btnDelete);
                    TextView btnRemoveReaction = sheetView.findViewById(R.id.btnRemoveReaction);

                    // Chỉ người gửi mới thấy nút Thu hồi
                    if (msg.getSenderId().equals(currentUserId)) {
                        btnDelete.setVisibility(View.VISIBLE);
                    } else {
                        btnDelete.setVisibility(View.GONE);
                    }

                    // Chỉ hiện nút "Gỡ cảm xúc" khi tin nhắn đang có reaction
                    if (msg.getReactionEmoji() != null && !msg.getReactionEmoji().isEmpty()) {
                        btnRemoveReaction.setVisibility(View.VISIBLE);
                    } else {
                        btnRemoveReaction.setVisibility(View.GONE);
                    }

                    // Gắn sự kiện Click cho các nút cảm xúc
                    sheetView.findViewById(R.id.btnReactLike).setOnClickListener(view -> {
                        listener.onReact(msg, "👍");
                        bottomSheetDialog.dismiss();
                    });

                    sheetView.findViewById(R.id.btnReactLove).setOnClickListener(view -> {
                        listener.onReact(msg, "❤️");
                        bottomSheetDialog.dismiss();
                    });

                    sheetView.findViewById(R.id.btnReactCustom).setOnClickListener(view -> {
                        listener.onReact(msg, "CUSTOM");
                        bottomSheetDialog.dismiss();
                    });

                    // Sự kiện Gỡ cảm xúc
                    btnRemoveReaction.setOnClickListener(view -> {
                        listener.onReact(msg, ""); // Truyền chuỗi rỗng để xóa
                        bottomSheetDialog.dismiss();
                    });

                    btnDelete.setOnClickListener(view -> {
                        listener.onDelete(msg);
                        bottomSheetDialog.dismiss();
                    });

                    bottomSheetDialog.show();
                }
                return true;
            });
        }
    }
}