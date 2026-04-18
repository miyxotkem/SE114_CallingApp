package com.example.se114_callingsystem;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
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
    private String currentUserId = "L2j7rDA0Y0cmsO0XNcaW"; // ID của Nhã
    private OnChatInteractListener listener;

    public interface OnChatInteractListener {
        void onReply(MessageModel message);
        void onDelete(MessageModel message);
        void onReact(MessageModel message, String emoji);
    }

    public Chat_adapter(List<MessageModel> messages, OnChatInteractListener listener) {
        this.mMessages = messages;
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
        MessageModel message = mMessages.get(position);
        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();

        // Lấy density để quy đổi dp sang px
        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;

        // 1. Logic gom nhóm tin nhắn
        boolean isFirstInGroup = true;
        if (position > 0) {
            MessageModel previousMsg = mMessages.get(position - 1);
            if (previousMsg.getSenderId().equals(message.getSenderId())) {
                isFirstInGroup = false;
            }
        }

        boolean isLastInGroup = true;
        if (position < mMessages.size() - 1) {
            MessageModel nextMsg = mMessages.get(position + 1);
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
            ((SentMessageViewHolder) holder).bind(message, listener, currentUserId, isLastInGroup);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(message, isFirstInGroup, isLastInGroup, listener, currentUserId);
        }
    }

    @Override
    public int getItemCount() {
        return mMessages != null ? mMessages.size() : 0;
    }

    // --- VIEWHOLDERS ---

    public static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, textReaction, textRepliedTo, textTime, tvFileName;
        ImageView ivMessageImage;
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
            layoutFile = itemView.findViewById(R.id.layoutFile);
            tvFileName = itemView.findViewById(R.id.tvFileName);
        }

        void bind(MessageModel message, OnChatInteractListener listener, String currentUserId, boolean isLastInGroup) {
            bindSharedLogic(message, messageText, ivMessageImage, layoutFile, tvFileName, textReaction, textRepliedTo, cardBubble, listener, currentUserId);

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
        ImageView avatarImg, ivMessageImage;
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
            layoutFile = itemView.findViewById(R.id.layoutFile);
            tvFileName = itemView.findViewById(R.id.tvFileName);
        }

        void bind(MessageModel message, boolean isFirstInGroup, boolean isLastInGroup, OnChatInteractListener listener, String currentUserId) {
            bindSharedLogic(message, messageText, ivMessageImage, layoutFile, tvFileName, textReaction, textRepliedTo, cardBubble, listener, currentUserId);

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
            int[] colors = {Color.RED, Color.BLUE, Color.parseColor("#FF9800"), Color.parseColor("#4CAF50"), Color.MAGENTA};
            return colors[Math.abs(hash) % colors.length];
        }
    }

    private static void bindSharedLogic(MessageModel msg, TextView textMessage, ImageView ivMessageImage, LinearLayout layoutFile, TextView tvFileName, TextView textReaction, TextView textRepliedTo, View cardBubble, OnChatInteractListener listener, String currentUserId) {
        if (msg.isDeleted()) {
            textMessage.setVisibility(View.VISIBLE);
            textMessage.setText("Tin nhắn đã bị thu hồi");
            textMessage.setTypeface(null, Typeface.ITALIC);
            textMessage.setTextColor(Color.GRAY);
            if (ivMessageImage != null) ivMessageImage.setVisibility(View.GONE);
            if (layoutFile != null) layoutFile.setVisibility(View.GONE);
            if (textReaction != null) textReaction.setVisibility(View.GONE);
            if (textRepliedTo != null) textRepliedTo.setVisibility(View.GONE);
        } else {
            textMessage.setTypeface(null, Typeface.NORMAL);
            textMessage.setTextColor(Color.BLACK);

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
                            Intent intent = new Intent(context, Image_viewer.class);
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
                            Intent intent = new Intent(context, Document_viewer.class);
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

            // Xử lý Reply Indicator
            if (textRepliedTo != null) {
                if (msg.getRepliedToContent() != null && !msg.getRepliedToContent().isEmpty()) {
                    String replyContent = msg.getRepliedToContent();
                    if (replyContent.length() > 30) replyContent = replyContent.substring(0, 30) + "...";
                    textRepliedTo.setText("Đang trả lời: " + replyContent);
                    textRepliedTo.setVisibility(View.VISIBLE);
                } else {
                    textRepliedTo.setVisibility(View.GONE);
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