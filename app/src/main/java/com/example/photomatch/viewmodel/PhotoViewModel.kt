package com.example.photomatch.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photomatch.util.FaceNetHelper
import com.example.photomatch.util.GalleryHelper
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.math.tanh

class PhotoViewModel : ViewModel() {
    var TAG = "PhotoViewModel"
    private val _matchingPhotos = MutableLiveData<List<Uri>>()
    val matchingPhotos: LiveData<List<Uri>> = _matchingPhotos

    private val _processingProgress = MutableLiveData<Int>()
    val processingProgress: LiveData<Int> = _processingProgress

    fun findMatchingFaces(context: Context, referenceEmbedding: FloatArray) {
        viewModelScope.launch {
            val matchingImages = mutableListOf<Uri>()
            val galleryImages = GalleryHelper.getGalleryImages(context)

            Log.d(TAG, "Reference embedding: ${referenceEmbedding.take(5)}") // Print first 5 values
            Log.d(TAG, "Total gallery images found: ${galleryImages.size}")


            if (galleryImages.isEmpty()) {
                _matchingPhotos.postValue(emptyList())
                return@launch
            }


            for ((index, imageUri) in galleryImages.withIndex()) {

                    try {
                        val bitmap =
                            MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                        val candidateEmbedding = FaceNetHelper.getFaceEmbeddings(bitmap, context)
                        Log.d("FaceMatching", "Candidate embedding: ${candidateEmbedding.take(5)}")
                        val similarity =
                            calculateCosineSimilarity(referenceEmbedding, candidateEmbedding)
                        Log.d("FaceMatching", "Cosine Similarity: $similarity")



                        if (similarity > 0.60f) {
                            // Match threshold, you can adjust this value current = 0.6F Float
                            Log.d(
                                TAG,
                                "Match found! referenceEmbedding - ${referenceEmbedding.toList()}, candidateEmbedding - ${candidateEmbedding.toList()}"
                            )
//                            synchronized(matchingImages) {
                                matchingImages.add(imageUri)
//                            }
                        }

                        // for process
                        val progress = ((index + 1) * 100) / galleryImages.size
                        _processingProgress.postValue(progress)

                    } catch (e: Exception) {
                        Log.e("PhotoViewModel", "Error processing image: ${e.message}")

                    }
                }
            _matchingPhotos.postValue(matchingImages)
        }
    }

    private fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            normA += embedding1[i] * embedding1[i]
            normB += embedding2[i] * embedding2[i]
        }

        normA = sqrt(normA)
        normB = sqrt(normB)

        return dotProduct / (normA * normB)
    }

}