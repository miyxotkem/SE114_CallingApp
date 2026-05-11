package com.example.se114_callingsystem.Adapter;

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
import com.example.se114_callingsystem.Activity.Page.DocumentViewerActivity;
import com.example.se114_callingsystem.Activity.Page.ImageViewerActivity;
import com.example.se114_callingsystem.Model.Message;
import com.example.se114_callingsystem.R;
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

    private List<Message> mMessages;
    private static FirebaseFirestore db;
    private String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "UNKNOWN";
    private OnChatInteractListener listener;
    private String serverColor = "#6C63FF";

    public interface OnChatInteractListener {
        void onReply(Message message);
        void onDelete(Message message);
        void onReact(Message message, String emoji);
    }

    public ChatAdapter(List<Message> messages, String serverColor, OnChatInteractListener listener) {
        this.mMessages = messages;
        this.serverColor = serverColor;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
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
        if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(message, listener, currentUserId, isLastInGroup, serverColor);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(message, isFirstInGroup, isLastInGroup, listener, currentUserId, serverColor);
        }
    }

    @Override
    public int getItemCount() {
        return mMessages != null ? mMessages.size() : 0;
    }

    // --- VIEWHOLDERS ---

    public static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, textReaction, textRepliedTo, textTime, tvFileName;
        ImageView ivMessageImage, ivRepliedImage;
        LinearLayout layoutFile;
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
        }

        void bind(Message message, OnChatInteractListener listener, String currentUserId, boolean isLastInGroup, String serverColor) {
            bindSharedLogic(message, messageText, ivMessageImage, layoutFile, tvFileName, textReaction, textRepliedTo, ivRepliedImage, cardBubble, listener, currentUserId, serverColor);

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
        LinearLayout layoutFile;
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
        }

        void bind(Message message, boolean isFirstInGroup, boolean isLastInGroup, OnChatInteractListener listener, String currentUserId, String serverColor) {
            bindSharedLogic(message, messageText, ivMessageImage, layoutFile, tvFileName, textReaction, textRepliedTo, ivRepliedImage, cardBubble, listener, currentUserId, serverColor);
            // Xử lý Tên (Hiện ở tin đầu nhóm)
            if (isFirstInGroup && senderName != null) {
                senderName.setVisibility(View.VISIBLE);
                String uid = message.getSenderId();
                senderName.setTag(uid);
                senderName.setTextColor(getConsistentColor(uid));

                db.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists() && uid.equals(senderName.getTag())) {
                        senderName.setText(documentSnapshot.getString("username"));
                    }
                });
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

    private static void bindSharedLogic(Message msg, TextView textMessage, ImageView ivMessageImage, LinearLayout layoutFile, TextView tvFileName, TextView textReaction, TextView textRepliedTo, ImageView ivRepliedImage, View cardBubble, OnChatInteractListener listener, String currentUserId, String serverColor) {
        Context ctx = textMessage.getContext();
        boolean isSentByMe = msg.getSenderId() != null && msg.getSenderId().equals(currentUserId);
        
        if (isSentByMe && cardBubble instanceof androidx.cardview.widget.CardView) {
            try {
                int color = android.graphics.Color.parseColor(serverColor);
                View innerLayout = ((androidx.cardview.widget.CardView) cardBubble).getChildAt(0);
                if (innerLayout != null && innerLayout.getBackground() != null) {
                    innerLayout.getBackground().mutate().setTint(color);
                }
            } catch (Exception e) {}
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
                            Context context = ivMessageImage.getContext();
                            Intent intent = new Intent(context, ImageViewerActivity.class);
                            intent.putExtra("IMAGE_URL", msg.getContent());
                            context.startActivity(intent);
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
                            Context context = layoutFile.getContext();
                            Intent intent = new Intent(context, DocumentViewerActivity.class);
                            intent.putExtra("FILE_URL", fileUrl);
                            intent.putExtra("FILE_NAME", fileName);
                            context.startActivity(intent);
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
            } else {
                // Tin nhắn văn bản bình thường
                textMessage.setVisibility(View.VISIBLE);
                textMessage.setText(msg.getContent());
                
                // Tự động nhận diện URL, gạch chân và cho phép click
                android.text.util.Linkify.addLinks(textMessage, android.text.util.Linkify.WEB_URLS);
                
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

            cardBubble.setOnLongClickListener(v -> {
                if (!msg.isDeleted()) {
                    BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(v.getContext());
                    View sheetView = LayoutInflater.from(v.getContext()).inflate(R.layout.layout_bottom_sheet_menu, null);
                    bottomSheetDialog.setContentView(sheetView);

                    try { ((View) sheetView.getParent()).setBackgroundColor(Color.TRANSPARENT); } catch (Exception e) {}

                    TextView btnDelete = sheetView.findViewById(R.id.btnDelete);
                    TextView btnRemoveReaction = sheetView.findViewById(R.id.btnRemoveReaction);

                    btnDelete.setVisibility(msg.getSenderId().equals(currentUserId) ? View.VISIBLE : View.GONE);
                    btnRemoveReaction.setVisibility((msg.getReactionEmoji() != null && !msg.getReactionEmoji().isEmpty()) ? View.VISIBLE : View.GONE);

                    sheetView.findViewById(R.id.btnReactLike).setOnClickListener(view -> { listener.onReact(msg, "👍"); bottomSheetDialog.dismiss(); });
                    sheetView.findViewById(R.id.btnReactLove).setOnClickListener(view -> { listener.onReact(msg, "❤️"); bottomSheetDialog.dismiss(); });
                    btnRemoveReaction.setOnClickListener(view -> { listener.onReact(msg, ""); bottomSheetDialog.dismiss(); });
                    btnDelete.setOnClickListener(view -> { listener.onDelete(msg); bottomSheetDialog.dismiss(); });

                    bottomSheetDialog.show();
                }
                return true;
            });
        }
    }
}