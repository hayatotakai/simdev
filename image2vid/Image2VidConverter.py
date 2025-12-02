import os
import cv2
import imageio
import ttkbootstrap as tb
from ttkbootstrap.constants import *
from tkinter import filedialog, messagebox

"""
Image Sequence to MP4 Converter
--------------------------------

Converts a folder of sequential images into an MP4 video with selectable speed,
frame dropping, and adjustable duration. Provides a modern GUI using ttkbootstrap.

Features:
- Select image folder
- Enter original video duration
- Select playback speed: 1x, 2x, 4x, or custom
- Frame dropping to target FPS (1–30)
- Dynamic display of original FPS, target FPS, estimated output FPS, and output duration
- Progress bar for video creation
- Cross-platform path normalization
- Modern bright UI

Author: Hayato Takai
Last Modified: 12/2/2025
"""

def update_fps_and_duration():
    """
    Updates the displayed FPS and output duration labels.

    Calculates:
    - Original FPS: total frames / original duration
    - Target FPS: desired FPS based on playback speed (capped 1–30)
    - Estimated Output FPS: actual FPS after frame dropping
    - Output Duration: original duration / speed

    Updates the UI labels accordingly.
    """
    folder = folder_path.get()
    if not os.path.isdir(folder):
        return

    try:
        duration = float(duration_entry.get())
        if duration <= 0:
            return
    except:
        return

    images = [f for f in os.listdir(folder) if f.lower().endswith((".png", ".jpg", ".jpeg", ".bmp", ".tif"))]
    if not images:
        return

    total_frames = len(images)

    # Original FPS
    orig_fps = total_frames / duration

    # Determine playback speed
    speed_val = speed_var.get()
    if speed_val == "custom":
        try:
            speed = float(custom_speed_entry.get())
            if speed <= 0:
                return
        except:
            return
    else:
        speed = float(speed_val)

    output_duration = duration / speed
    target_fps = min(30, max(1, int(total_frames / output_duration)))
    num_selected_frames = int(output_duration * target_fps)
    est_output_fps = num_selected_frames / output_duration

    # Update labels
    lbl_orig_fps.config(text=f"Original FPS: {orig_fps:.2f}")
    lbl_target_fps.config(text=f"Target FPS: {target_fps}")
    lbl_est_fps.config(text=f"Estimated Output FPS: {est_output_fps:.2f}")
    lbl_output_duration.config(text=f"Output Duration: {output_duration:.2f} s")


def build_video():
    folder = folder_path.get()
    folder = os.path.normpath(folder)  # normalize path
    if not folder or not os.path.isdir(folder):
        messagebox.showerror("Error", "Please select a valid image folder.")
        return

    try:
        duration = float(duration_entry.get())
        if duration <= 0:
            raise ValueError
    except:
        messagebox.showerror("Error", "Enter a valid video duration (seconds).")
        return

    # Speed selection
    speed_val = speed_var.get()
    if speed_val == "custom":
        try:
            speed = float(custom_speed_entry.get())
            if speed <= 0:
                raise ValueError
        except:
            messagebox.showerror("Error", "Enter a valid custom speed multiplier.")
            return
    else:
        speed = float(speed_val)

    # Collect images
    images = [os.path.join(folder, f) for f in sorted(os.listdir(folder))
              if f.lower().endswith((".png", ".jpg", ".jpeg", ".bmp", ".tif"))]
    if not images:
        messagebox.showerror("Error", "No valid images found in the folder.")
        return

    total_frames_original = len(images)
    output_duration = duration / speed
    target_fps = min(30, max(1, int(total_frames_original / output_duration)))
    step = total_frames_original / (output_duration * target_fps)
    selected_frames = [images[int(i * step)] for i in range(int(output_duration * target_fps))]

    output_file = os.path.normpath(os.path.join(folder, "output_video.mp4"))
    first_frame = cv2.imread(selected_frames[0])
    writer = imageio.get_writer(output_file, fps=target_fps, codec="libx264", quality=8)

    progress_bar["maximum"] = len(selected_frames)
    progress_bar["value"] = 0

    for idx, frame_path in enumerate(selected_frames):
        frame = cv2.imread(frame_path)
        frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        writer.append_data(frame)

        progress_bar["value"] = idx + 1
        app.update_idletasks()

    writer.close()
    messagebox.showinfo("Done", f"Video created:\n{output_file}")


def browse_folder():
    """
    Opens a folder selection dialog and updates the folder path.
    
    Also updates FPS and duration labels dynamically.
    """    
    folder = filedialog.askdirectory()
    if folder:
        folder_path.set(os.path.normpath(folder))
        update_fps_and_duration()


def update_custom_entry_state(*args):
    """
    Enables or disables the custom speed entry box.

    - If 'Custom' speed is selected: enables entry box (white)
    - Otherwise: disables entry box (gray)
    
    Updates FPS and output duration labels.
    """
    if speed_var.get() == "custom":
        custom_speed_entry.config(state=NORMAL, background="white")
    else:
        custom_speed_entry.config(state=DISABLED, background="#d9d9d9")  # grayed out
    update_fps_and_duration()


# ---------------- Modern Bright UI ---------------- #
app = tb.Window(themename="flatly")
app.title("Image Sequence → MP4 Converter - Hayato Takai")

# Original size
app.geometry("1000x1000")

# Minimum size
app.minsize(500, 700)

folder_path = tb.StringVar()
speed_var = tb.StringVar(value="1")

# Title
tb.Label(app, text="Image Sequence to MP4", font=("Segoe UI", 18, "bold")).pack(pady=10)

frame = tb.Frame(app, padding=10)
frame.pack(fill=BOTH, expand=True)

# Folder selection
tb.Label(frame, text="Image Folder:").pack(anchor="w")
tb.Entry(frame, textvariable=folder_path).pack(fill=X, pady=3)
tb.Button(frame, text="Browse", bootstyle=PRIMARY, command=browse_folder).pack(pady=5)

# Duration
tb.Label(frame, text="Original Video Duration (seconds):").pack(anchor="w", pady=(10,0))
duration_entry = tb.Entry(frame)
duration_entry.pack(fill=X, pady=3)
duration_entry.bind("<KeyRelease>", lambda e: update_fps_and_duration())

# Speed selector
tb.Label(frame, text="Playback Speed:").pack(anchor="w", pady=(10,0))
speed_frame = tb.Frame(frame)
speed_frame.pack(pady=3)

for val, text in [("1", "1×"), ("2", "2×"), ("4", "4×")]:
    tb.Radiobutton(speed_frame, text=text, value=val, variable=speed_var, bootstyle="success").pack(side="left", padx=5)

tb.Radiobutton(speed_frame, text="Custom", value="custom", variable=speed_var, bootstyle="info").pack(side="left", padx=5)

custom_speed_entry = tb.Entry(frame, state=DISABLED, background="#d9d9d9")
custom_speed_entry.pack(fill=X, pady=3)
custom_speed_entry.bind("<KeyRelease>", lambda e: update_fps_and_duration())

speed_var.trace_add("write", update_custom_entry_state)

# FPS and Duration display labels
lbl_orig_fps = tb.Label(frame, text="Original FPS: N/A")
lbl_orig_fps.pack(anchor="w", pady=2)
lbl_target_fps = tb.Label(frame, text="Target FPS: N/A")
lbl_target_fps.pack(anchor="w", pady=2)
lbl_est_fps = tb.Label(frame, text="Estimated Output FPS: N/A")
lbl_est_fps.pack(anchor="w", pady=2)
lbl_output_duration = tb.Label(frame, text="Output Duration: N/A")
lbl_output_duration.pack(anchor="w", pady=2)

# Create Video Button
tb.Button(app, text="Create MP4", bootstyle=SUCCESS, command=build_video, width=20).pack(pady=10)

# Progress bar
progress_bar = tb.Progressbar(app, bootstyle=STRIPED, length=480)
progress_bar.pack(pady=15)


footer_frame = tb.Frame(app, padding=(5,5))
footer_frame.pack(side="bottom", fill=X)

tb.Label(
    footer_frame,
    text="To report errors, please contact: hayato.takai@hydacusa.com",
    font=("Segoe UI", 10),
    foreground="gray"
).pack(anchor="center")
app.mainloop()
