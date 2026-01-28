package com.example.myfslapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ExperimentalGetImage;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ExperimentalGetImage
public class DictionaryActivity extends AppCompatActivity {

    private RecyclerView rvCategories, rvGestures;
    private ImageButton btnClose;
    private TextView tvTitle;
    private LinearLayout tabAlphabet, tabWords;
    private View indicatorAlphabet, indicatorWords;

    private CategoryAdapter categoryAdapter;
    private GestureAdapter gestureAdapter;

    // Background thread pool + main-thread handler for image loading
    private final ExecutorService imageLoader = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static final String MODEL_TYPE_ALPHABET = "alphabet";
    public static final String MODEL_TYPE_GESTURE  = "gesture";
    public static final String MODEL_TYPE_GESTURES = "gesture";

    private String currentModelType = MODEL_TYPE_ALPHABET;
    private String selectedCategory = null;

    private static final String CAT_GREETINGS = "Greetings";
    private static final String CAT_FAMILY    = "Family";
    private static final String CAT_DAYS      = "Days";
    private static final String CAT_NUMBERS   = "Numbers";
    private static final String CAT_COLORS    = "Colors";
    private static final String CAT_FOOD      = "Food & Drinks";
    private static final String CAT_OTHERS    = "Others";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary);

        Intent intent = getIntent();
        if (intent.hasExtra("MODEL_TYPE")) {
            String raw = intent.getStringExtra("MODEL_TYPE");
            currentModelType = (raw != null && raw.startsWith("gesture"))
                    ? MODEL_TYPE_GESTURE : MODEL_TYPE_ALPHABET;
        }

        bindViews();
        setupTabs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        imageLoader.shutdownNow();
    }

    private void bindViews() {
        rvCategories      = findViewById(R.id.rvCategories);
        rvGestures        = findViewById(R.id.rvGestures);
        btnClose          = findViewById(R.id.btnClose);
        tvTitle           = findViewById(R.id.tvTitle);
        tabAlphabet       = findViewById(R.id.tabAlphabet);
        tabWords          = findViewById(R.id.tabWords);
        indicatorAlphabet = findViewById(R.id.indicatorAlphabet);
        indicatorWords    = findViewById(R.id.indicatorWords);
        btnClose.setOnClickListener(v -> finish());
    }

    private void setupTabs() {
        tabAlphabet.setOnClickListener(v -> switchTab(MODEL_TYPE_ALPHABET));
        tabWords.setOnClickListener(v -> switchTab(MODEL_TYPE_GESTURE));
        switchTab(currentModelType);
    }

    private void switchTab(String modelType) {
        currentModelType = modelType;
        selectedCategory = null;

        if (MODEL_TYPE_ALPHABET.equals(modelType)) {
            indicatorAlphabet.setVisibility(View.VISIBLE);
            indicatorWords.setVisibility(View.INVISIBLE);
            tvTitle.setText("ALPHABET DICTIONARY");
            rvCategories.setVisibility(View.GONE);
            loadAlphabetGestures();
        } else {
            indicatorAlphabet.setVisibility(View.INVISIBLE);
            indicatorWords.setVisibility(View.VISIBLE);
            tvTitle.setText("GESTURE DICTIONARY");
            rvCategories.setVisibility(View.VISIBLE);
            loadCategories();
        }
    }

    /**
     * Loads a bitmap from assets on a background thread, then posts the result
     * back to the main thread. Uses ImageView.setTag() to prevent a recycled
     * ViewHolder from showing a stale image.
     */
    private void loadImageAsync(ImageView imageView, String assetPath) {
        imageView.setTag(assetPath);          // mark which image this view expects
        imageView.setImageBitmap(null);       // clear previous image immediately
        imageView.setBackgroundColor(0xFF2A2A2A); // dark grey placeholder

        imageLoader.execute(() -> {
            Bitmap bmp = null;
            try {
                InputStream is = getAssets().open(assetPath);
                bmp = BitmapFactory.decodeStream(is);
                is.close();
            } catch (IOException ignored) { }

            final Bitmap result = bmp;
            mainHandler.post(() -> {
                // Only apply if this view still wants THIS image (not recycled)
                if (assetPath.equals(imageView.getTag())) {
                    if (result != null) {
                        imageView.setBackgroundColor(0x00000000); // clear placeholder
                        imageView.setImageBitmap(result);
                    } else {
                        // Red tint = image file is missing from assets
                        imageView.setBackgroundColor(0xFFCC3333);
                    }
                }
            });
        });
    }

    // ── Data loading ──────────────────────────────────────────────────────

    private void loadCategories() {
        List<String> categories = Arrays.asList(
                CAT_GREETINGS, CAT_FAMILY, CAT_DAYS, CAT_NUMBERS,
                CAT_COLORS, CAT_FOOD, CAT_OTHERS
        );
        rvCategories.setLayoutManager(new LinearLayoutManager(this));
        categoryAdapter = new CategoryAdapter(categories, category -> {
            selectedCategory = category;
            loadGesturesByCategory(category);
        });
        rvCategories.setAdapter(categoryAdapter);
        selectedCategory = CAT_GREETINGS;
        loadGesturesByCategory(CAT_GREETINGS);
    }

    private void loadAlphabetGestures() {
        List<GestureItem> items = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            items.add(new GestureItem(
                    String.valueOf(c),
                    String.valueOf(c),
                    "fsl_gesture_images/letter_" + Character.toLowerCase(c) + ".png"
            ));
        }
        rvGestures.setLayoutManager(new GridLayoutManager(this, 2));
        gestureAdapter = new GestureAdapter(items);
        rvGestures.setAdapter(gestureAdapter);
    }

    private void loadGesturesByCategory(String category) {
        List<GestureItem> items = new ArrayList<>();
        switch (category) {
            case CAT_GREETINGS:
                items.add(new GestureItem("Hello",            "Hello / Hi",       "fsl_gesture_images/gesture_hello.png"));
                items.add(new GestureItem("Good morning",     "Magandang umaga",  "fsl_gesture_images/gesture_good_morning.png"));
                items.add(new GestureItem("Good afternoon",   "Magandang hapon",  "fsl_gesture_images/gesture_good_afternoon.png"));
                items.add(new GestureItem("Good evening",     "Magandang gabi",   "fsl_gesture_images/gesture_good_evening.png"));
                items.add(new GestureItem("Thank you",        "Salamat",          "fsl_gesture_images/gesture_thank_you.png"));
                items.add(new GestureItem("You're welcome",   "Walang anuman",    "fsl_gesture_images/gesture_youre_welcome.png"));
                items.add(new GestureItem("Nice to meet you", "Nice to meet you", "fsl_gesture_images/gesture_nice_to_meet_you.png"));
                items.add(new GestureItem("See you tomorrow", "See you tomorrow", "fsl_gesture_images/gesture_see_you_tomorrow.png"));
                items.add(new GestureItem("How are you",      "Kumusta ka?",      "fsl_gesture_images/gesture_how_are_you.png"));
                items.add(new GestureItem("I'm fine",         "Ayos lang ako",    "fsl_gesture_images/gesture_im_fine.png"));
                break;
            case CAT_FAMILY:
                items.add(new GestureItem("Mother",      "Nanay",    "fsl_gesture_images/gesture_mother.png"));
                items.add(new GestureItem("Father",      "Tatay",    "fsl_gesture_images/gesture_father.png"));
                items.add(new GestureItem("Parents",     "Magulang", "fsl_gesture_images/gesture_parents.png"));
                items.add(new GestureItem("Grandfather", "Lolo",     "fsl_gesture_images/gesture_grandfather.png"));
                items.add(new GestureItem("Grandmother", "Lola",     "fsl_gesture_images/gesture_grandmother.png"));
                items.add(new GestureItem("Uncle",       "Tito",     "fsl_gesture_images/gesture_uncle.png"));
                items.add(new GestureItem("Auntie",      "Tita",     "fsl_gesture_images/gesture_auntie.png"));
                items.add(new GestureItem("Cousin",      "Pinsan",   "fsl_gesture_images/gesture_cousin.png"));
                items.add(new GestureItem("Married",     "Kasal",    "fsl_gesture_images/gesture_married.png"));
                break;
            case CAT_DAYS:
                items.add(new GestureItem("Monday",    "Lunes",      "fsl_gesture_images/gesture_monday.png"));
                items.add(new GestureItem("Tuesday",   "Martes",     "fsl_gesture_images/gesture_tuesday.png"));
                items.add(new GestureItem("Wednesday", "Miyerkules", "fsl_gesture_images/gesture_wednesday.png"));
                items.add(new GestureItem("Thursday",  "Huwebes",    "fsl_gesture_images/gesture_thursday.png"));
                items.add(new GestureItem("Friday",    "Biyernes",   "fsl_gesture_images/gesture_friday.png"));
                items.add(new GestureItem("Saturday",  "Sabado",     "fsl_gesture_images/gesture_saturday.png"));
                items.add(new GestureItem("Sunday",    "Linggo",     "fsl_gesture_images/gesture_sunday.png"));
                items.add(new GestureItem("Today",     "Ngayon",     "fsl_gesture_images/gesture_today.png"));
                items.add(new GestureItem("Tomorrow",  "Bukas",      "fsl_gesture_images/gesture_tomorrow.png"));
                break;
            case CAT_NUMBERS:
                for (int i = 1; i <= 10; i++) {
                    items.add(new GestureItem(String.valueOf(i), String.valueOf(i),
                            "fsl_gesture_images/gesture_" + i + ".png"));
                }
                break;
            case CAT_COLORS:
                items.add(new GestureItem("Black",  "Itim",       "fsl_gesture_images/gesture_black.png"));
                items.add(new GestureItem("Blue",   "Asul",       "fsl_gesture_images/gesture_blue.png"));
                items.add(new GestureItem("Brown",  "Kayumanggi", "fsl_gesture_images/gesture_brown.png"));
                items.add(new GestureItem("Gray",   "Abo",        "fsl_gesture_images/gesture_gray.png"));
                items.add(new GestureItem("Green",  "Berde",      "fsl_gesture_images/gesture_green.png"));
                items.add(new GestureItem("Orange", "Kahel",      "fsl_gesture_images/gesture_orange.png"));
                items.add(new GestureItem("Pink",   "Rosas",      "fsl_gesture_images/gesture_pink.png"));
                items.add(new GestureItem("Red",    "Pula",       "fsl_gesture_images/gesture_red.png"));
                items.add(new GestureItem("Violet", "Lila",       "fsl_gesture_images/gesture_violet.png"));
                items.add(new GestureItem("White",  "Puti",       "fsl_gesture_images/gesture_white.png"));
                items.add(new GestureItem("Yellow", "Dilaw",      "fsl_gesture_images/gesture_yellow.png"));
                break;
            case CAT_FOOD:
                items.add(new GestureItem("Bread",      "Tinapay",       "fsl_gesture_images/gesture_bread.png"));
                items.add(new GestureItem("Rice",       "Kanin",         "fsl_gesture_images/gesture_rice.png"));
                items.add(new GestureItem("Egg",        "Itlog",         "fsl_gesture_images/gesture_egg.png"));
                items.add(new GestureItem("Meat",       "Karne",         "fsl_gesture_images/gesture_meat.png"));
                items.add(new GestureItem("Fish",       "Isda",          "fsl_gesture_images/gesture_fish.png"));
                items.add(new GestureItem("Chicken",    "Manok",         "fsl_gesture_images/gesture_chicken.png"));
                items.add(new GestureItem("Shrimp",     "Hipon",         "fsl_gesture_images/gesture_shrimp.png"));
                items.add(new GestureItem("Crab",       "Alimango",      "fsl_gesture_images/gesture_crab.png"));
                items.add(new GestureItem("Longganisa", "Longganisa",    "fsl_gesture_images/gesture_longganisa.png"));
                items.add(new GestureItem("Coffee",     "Kape",          "fsl_gesture_images/gesture_coffee.png"));
                items.add(new GestureItem("Tea",        "Tsaa",          "fsl_gesture_images/gesture_tea.png"));
                items.add(new GestureItem("Juice",      "Juice",         "fsl_gesture_images/gesture_juice.png"));
                items.add(new GestureItem("Beer",       "Bir",           "fsl_gesture_images/gesture_beer.png"));
                items.add(new GestureItem("Wine",       "Alak",          "fsl_gesture_images/gesture_wine.png"));
                items.add(new GestureItem("Sugar",      "Asukal",        "fsl_gesture_images/gesture_sugar.png"));
                items.add(new GestureItem("No sugar",   "Walang asukal", "fsl_gesture_images/gesture_no_sugar.png"));
                break;
            case CAT_OTHERS:
                items.add(new GestureItem("Yes",               "Oo",                 "fsl_gesture_images/gesture_yes.png"));
                items.add(new GestureItem("No",                "Hindi",              "fsl_gesture_images/gesture_no.png"));
                items.add(new GestureItem("You",               "Ikaw",               "fsl_gesture_images/gesture_you.png"));
                items.add(new GestureItem("Good",              "Mabuti",             "fsl_gesture_images/gesture_good.png"));
                items.add(new GestureItem("Correct",           "Tama",               "fsl_gesture_images/gesture_correct.png"));
                items.add(new GestureItem("Wrong",             "Mali",               "fsl_gesture_images/gesture_wrong.png"));
                items.add(new GestureItem("Know",              "Alam",               "fsl_gesture_images/gesture_know.png"));
                items.add(new GestureItem("Understand",        "Naintindihan",       "fsl_gesture_images/gesture_understand.png"));
                items.add(new GestureItem("Don't understand",  "Hindi maintindihan", "fsl_gesture_images/gesture_dont_understand.png"));
                items.add(new GestureItem("Fast",              "Mabilis",            "fsl_gesture_images/gesture_fast.png"));
                items.add(new GestureItem("Slow",              "Mabagal",            "fsl_gesture_images/gesture_slow.png"));
                items.add(new GestureItem("Hot",               "Mainit",             "fsl_gesture_images/gesture_hot.png"));
                items.add(new GestureItem("Cold",              "Malamig",            "fsl_gesture_images/gesture_cold.png"));
                items.add(new GestureItem("Deaf",              "Bingi",              "fsl_gesture_images/gesture_deaf.png"));
                items.add(new GestureItem("Blind",             "Bulag",              "fsl_gesture_images/gesture_blind.png"));
                items.add(new GestureItem("Deaf blind",        "Bingi at bulag",     "fsl_gesture_images/gesture_deaf_blind.png"));
                items.add(new GestureItem("Hard of hearing",   "Hard of hearing",    "fsl_gesture_images/gesture_hard_of_hearing.png"));
                items.add(new GestureItem("Wheelchair person", "Wheelchair person",  "fsl_gesture_images/gesture_wheelchair_person.png"));
                items.add(new GestureItem("I love you",        "Mahal kita",         "fsl_gesture_images/gesture_i_love_you.png"));
                items.add(new GestureItem("Love",              "Pag-ibig",           "fsl_gesture_images/gesture_love.png"));
                items.add(new GestureItem("Internet",          "Internet",           "fsl_gesture_images/gesture_internet.png"));
                items.add(new GestureItem("Cellphone",         "Cellphone",          "fsl_gesture_images/gesture_cellphone.png"));
                break;
        }
        rvGestures.setLayoutManager(new GridLayoutManager(this, 2));
        gestureAdapter = new GestureAdapter(items);
        rvGestures.setAdapter(gestureAdapter);
    }

    // ── Category Adapter ──────────────────────────────────────────────────
    class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
        private final List<String> categories;
        private final OnCategoryClickListener listener;

        interface OnCategoryClickListener { void onCategoryClick(String category); }

        CategoryAdapter(List<String> categories, OnCategoryClickListener listener) {
            this.categories = categories;
            this.listener   = listener;
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_category, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String category = categories.get(position);
            holder.tvCategory.setText(category);
            holder.itemView.setSelected(category.equals(selectedCategory));
            holder.itemView.setOnClickListener(v -> {
                listener.onCategoryClick(category);
                notifyDataSetChanged();
            });
        }

        @Override public int getItemCount() { return categories.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvCategory;
            ViewHolder(View view) {
                super(view);
                tvCategory = view.findViewById(R.id.tvCategory);
            }
        }
    }

    // ── Gesture Adapter ───────────────────────────────────────────────────
    class GestureAdapter extends RecyclerView.Adapter<GestureAdapter.ViewHolder> {
        private final List<GestureItem> items;

        GestureAdapter(List<GestureItem> items) { this.items = items; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_gesture, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GestureItem item = items.get(position);
            holder.tvGestureName.setText(item.getName());
            holder.tvGestureDesc.setText(item.getDescription());
            // ↓ Async load — no Glide, no main-thread blocking
            loadImageAsync(holder.ivGesture, item.getImageName());
        }

        @Override public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivGesture;
            TextView  tvGestureName, tvGestureDesc;
            ViewHolder(View view) {
                super(view);
                ivGesture     = view.findViewById(R.id.ivGesture);
                tvGestureName = view.findViewById(R.id.tvGestureName);
                tvGestureDesc = view.findViewById(R.id.tvGestureDesc);
            }
        }
    }
}