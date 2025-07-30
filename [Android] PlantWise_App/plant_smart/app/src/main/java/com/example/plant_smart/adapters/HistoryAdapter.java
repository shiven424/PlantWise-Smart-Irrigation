package com.example.plant_smart;
import com.example.plant_smart.models.HistoryData;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private List<HistoryData> historyList;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView tvTimestamp, tvValues;
        public ViewHolder(View view) {
            super(view);
            tvTimestamp = view.findViewById(R.id.textTimestamp);
            tvValues = view.findViewById(R.id.textValues);
        }
    }

    public HistoryAdapter(List<HistoryData> historyList) {
        this.historyList = historyList;
    }

    @NonNull
    @Override
    public HistoryAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryAdapter.ViewHolder holder, int position) {
        HistoryData data = historyList.get(position);
        holder.tvTimestamp.setText(data.getTimestamp());
        String values = "H: " + data.getHumidity() + "%, M: " + data.getSoilMoisture() + "%, B: " + data.getBrightness() + "%";
        holder.tvValues.setText(values);
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }
}
