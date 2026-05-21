package com.example.clonediscordapp.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.clonediscordapp.R;
import com.example.clonediscordapp.data.model.Participant;
import com.example.clonediscordapp.databinding.ItemVoiceParticipantBinding;

import java.util.ArrayList;
import java.util.List;

import io.agora.rtc2.Constants;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.video.VideoCanvas;

public class VoiceParticipantAdapter extends RecyclerView.Adapter<VoiceParticipantAdapter.ViewHolder> {

    private Context context;
    private List<Participant> participants;
    private RtcEngine rtcEngine;

    // Default constructor for backward compatibility
    public VoiceParticipantAdapter() {
        this.participants = new ArrayList<>();
    }

    public VoiceParticipantAdapter(Context context, List<Participant> participants, RtcEngine rtcEngine) {
        this.context = context;
        this.participants = participants;
        this.rtcEngine = rtcEngine;
    }

    public void submitList(List<Participant> list) {
        this.participants = list;
        notifyDataSetChanged();
    }

    public void setRtcEngine(RtcEngine rtcEngine) {
        this.rtcEngine = rtcEngine;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemVoiceParticipantBinding binding = ItemVoiceParticipantBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);

        // Dynamically calculate the item height based on participant count to divide screen space
        int totalItems = participants.size();
        int rows = getRowsCount(totalItems);

        ViewGroup.LayoutParams layoutParams = binding.getRoot().getLayoutParams();
        if (parent.getHeight() > 0) {
            layoutParams.height = parent.getHeight() / rows;
        } else {
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        binding.getRoot().setLayoutParams(layoutParams);

        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(participants.get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            String payload = payloads.get(0).toString();
            Participant participant = participants.get(position);

            if (payload.equals("border_update")) {
                holder.updateSpeakingBorder(participant.isSpeaking);
                return;
            } else if (payload.equals("state_update")) {
                holder.updateState(participant);
                return;
            }
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public int getItemCount() {
        return participants.size();
    }

    private int getRowsCount(int totalItems) {
        if (totalItems <= 1) return 1;
        if (totalItems <= 2) return 2;
        if (totalItems <= 4) return 2;
        return 3;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemVoiceParticipantBinding binding;

        public ViewHolder(ItemVoiceParticipantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Participant participant) {
            Context ctx = (context != null) ? context : itemView.getContext();
            
            // 1. Clear old views first to prevent overlapping rendering layers
            binding.videoContainer.removeAllViews();

            // 2. Instantiate and add a new SurfaceView
            SurfaceView surfaceView = new SurfaceView(ctx);
            binding.videoContainer.addView(surfaceView);

            // 3. Set up video rendering config from RtcEngine
            if (rtcEngine != null) {
                if (participant.name.equals("Màn hình của tôi")) {
                    VideoCanvas canvas = new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, 0);
                    canvas.sourceType = Constants.VIDEO_SOURCE_SCREEN_PRIMARY;
                    rtcEngine.setupLocalVideo(canvas);
                    rtcEngine.startPreview(Constants.VideoSourceType.VIDEO_SOURCE_SCREEN_PRIMARY);
                } else if (participant.name.contains("Me")) {
                    surfaceView.setZOrderMediaOverlay(true);
                    rtcEngine.setupLocalVideo(new VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0));
                } else {
                    surfaceView.setZOrderMediaOverlay(true);
                    int renderMode = (participant.uid >= 1000) ? VideoCanvas.RENDER_MODE_FIT : VideoCanvas.RENDER_MODE_HIDDEN;
                    rtcEngine.setupRemoteVideo(new VideoCanvas(surfaceView, renderMode, participant.uid));
                }
            }

            binding.tvName.setText(participant.name);

            // 4. Update the visual indicators (mute state, camera state, border)
            updateState(participant);
            updateSpeakingBorder(participant.isSpeaking);
        }

        public void updateState(Participant participant) {
            binding.videoContainer.setVisibility(participant.isVideoOff ? View.GONE : View.VISIBLE);
            binding.avatarContainer.setVisibility(participant.isVideoOff ? View.VISIBLE : View.GONE);
            binding.flMuteIndicator.setVisibility(participant.isMuted ? View.VISIBLE : View.GONE);
            binding.flVideoIndicator.setVisibility(!participant.isVideoOff ? View.VISIBLE : View.GONE);
            binding.flScreenIndicator.setVisibility(participant.isSharingScreen ? View.VISIBLE : View.GONE);
        }

        public void updateSpeakingBorder(boolean isSpeaking) {
            if (isSpeaking) {
                binding.cardParticipant.setBackgroundResource(R.drawable.bg_voice_participant_speaking);
                binding.vSpeakingDot.setVisibility(View.VISIBLE);
            } else {
                binding.cardParticipant.setBackgroundResource(R.drawable.bg_voice_participant);
                binding.vSpeakingDot.setVisibility(View.GONE);
            }
        }
    }
}
