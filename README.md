# Photo Match

Photo Match is an Android application that allows users to find similar faces from their gallery using machine learning. The app uses FaceNet for facial recognition and the ML Kit Face Detection API to detect faces. You can capture your photo and the app will search through your gallery to find similar faces using **Cosine Similarity**.

## Features
- **Face Detection**: Uses ML Kit Face Detection to detect faces in images.
- **Face Recognition**: Implements the FaceNet model (`facenet.tflite`) to extract facial embeddings.
- **Cosine Similarity**: Calculates the cosine similarity between face embeddings to find similar faces.
- **Gallery Search**: Capture a photo and find similar photos in your gallery.
- **Photo Matching**: Matches and shows all the photos from your gallery with similar faces.
  
## How It Works

1. **Capture a Photo**: The app captures a photo of the user's face using the camera.
2. **Face Detection**: The photo is processed by ML Kit's Face Detection API to detect the face and crop the face region.
3. **Face Embedding**: The cropped face is passed to the FaceNet model (`facenet.tflite`) to generate a 128-dimensional embedding representing the face.
4. **Cosine Similarity**: The embedding of the captured photo is compared to the embeddings of all photos in the gallery using cosine similarity.
5. **Find Matches**: Photos with embeddings having a cosine similarity higher than a certain threshold are displayed as similar photos.


## Video
<p align='center'>
  <video width="320" height="240" controls>
  <source src="https://firebasestorage.googleapis.com/v0/b/fir-demo-9e712.appspot.com/o/Screen_recording_20241014_183649.mp4?alt=media&token=7c4ee33d-ffd7-445b-9909-dbd3b079d51e" type="video/mp4">
</video>
</p>


