# Image Sequence to MP4 Converter

A Python application that converts a folder of sequentially numbered images into a **MP4 video** with control over playback speed, frame rate, and duration.

Built with **ttkbootstrap**

## Features

- Select any folder containing image sequences (PNG, JPG, JPEG, BMP, TIFF)
- Real-time folder validation with visual feedback (Valid / Invalid)
- Set the **original video duration** in seconds
- Choose playback speed: **0.5×, 1×, 2×, 4×**, or **custom**
- Smart frame dropping/sampling to achieve target FPS (1–30 FPS)
- Live calculation and display of:
  - Original FPS
  - Target FPS
  - Estimated output FPS
  - Final video duration
- Progress bar during video encoding
- Outputs `output_video.mp4` directly in the source folder
- Cross-platform (Windows, macOS, Linux)

## Requirements

- Python 3.7+
- OpenCV (`cv2`)
- imageio + imageio-ffmpeg
- ttkbootstrap

## Installation

```bash
pyinstaller --onefile --windowed     --hidden-import=cv2     --hidden-import=imageio     --hidden-import=imageio_ffmpeg     --hidden-import=ttkbootstrap     --hidden-import=imageio.plugins     --collect-all imageio     --collect-all ttkbootstrap     --collect-all PIL --icon=vid.ico     Image2VidConverter.py^C
