package com.example.myapplication.gallery;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.data.Memory;

public class MemoryAdapter extends ListAdapter<Memory, MemoryAdapter.ViewHolder> {
    public interface OnMemoryClickListener {
        void onMemoryClick(Memory memory);

        void onMemoryLongClick(Memory memory);
    }

    private static final DiffUtil.ItemCallback<Memory> DIFF = new DiffUtil.ItemCallback<Memory>() {
        @Override
        public boolean areItemsTheSame(@NonNull Memory a, @NonNull Memory b) {
            return a.getId() == b.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Memory a, @NonNull Memory b) {
            return a.equals(b);
        }
    };

    private final OnMemoryClickListener listener;
    private boolean gridMode = true;

    public MemoryAdapter(OnMemoryClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setGridMode(boolean gridMode) {
        if (this.gridMode == gridMode) return;
        this.gridMode = gridMode;
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_memory, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Memory memory = getItem(position);

        ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) holder.image.getLayoutParams();
        lp.dimensionRatio = gridMode ? "1:1" : "3:4";
        holder.image.setLayoutParams(lp);

        memory.loadInto(holder.image);
        holder.date.setText(memory.formatDate());
        holder.heart.setVisibility(memory.isFavorite() ? View.VISIBLE : View.GONE);
        holder.card.setContentDescription(memory.formatDate()
                + (memory.isFavorite() ? " ♥" : ""));
        holder.card.setOnClickListener(v -> listener.onMemoryClick(memory));
        holder.card.setOnLongClickListener(v -> {
            listener.onMemoryLongClick(memory);
            return true;
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView image;
        final View heart;
        final TextView date;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.memory_card);
            image = itemView.findViewById(R.id.memory_image);
            heart = itemView.findViewById(R.id.heart_badge);
            date = itemView.findViewById(R.id.memory_date);
        }
    }
}
