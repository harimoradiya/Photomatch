package com.example.photomatch.di

import com.example.photomatch.viewmodel.PhotoViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule =  module {
    viewModel { PhotoViewModel() } // Inject the repository into ViewModel
}