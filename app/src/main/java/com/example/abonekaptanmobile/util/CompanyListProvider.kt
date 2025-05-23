package com.example.abonekaptanmobile.util

import android.content.Context
import android.util.Log
import com.example.abonekaptanmobile.model.CompanySubscriptionInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides a list of known company subscription information by reading from a CSV file in assets.
 * This class is designed to be injected using Hilt.
 *
 * @property context The application context, used to access assets.
 */
@Singleton
class CompanyListProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CompanyListProvider"
        private const val CSV_FILE_NAME = "subscription_companies.csv"
    }

    /**
     * Loads the company subscription information from the "subscription_companies.csv" file in the assets folder.
     *
     * This function reads the CSV file line by line, skipping the header. Each subsequent line is
     * parsed to create a [CompanySubscriptionInfo] object. It handles potential [IOException] during
     * file reading and logs errors if a line is malformed or the file is not found.
     *
     * The operation is performed on the [Dispatchers.IO] dispatcher.
     *
     * @return A list of [CompanySubscriptionInfo] objects. Returns an empty list if the file
     *         cannot be read or in case of errors.
     */
    suspend fun loadCompanyList(): List<CompanySubscriptionInfo> = withContext(Dispatchers.IO) {
        val companyList = mutableListOf<CompanySubscriptionInfo>()
        try {
            context.assets.open(CSV_FILE_NAME).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    reader.readLine() // Skip header line

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val tokens = line!!.split(",")
                        if (tokens.size == 3) {
                            val companyName = tokens[0].trim()
                            val category = tokens[1].trim()
                            val serviceType = tokens[2].trim()
                            if (companyName.isNotEmpty() && category.isNotEmpty() && serviceType.isNotEmpty()) {
                                companyList.add(CompanySubscriptionInfo(companyName, category, serviceType))
                            } else {
                                Log.w(TAG, "Skipping malformed line (empty fields): $line")
                            }
                        } else {
                            Log.w(TAG, "Skipping malformed line (incorrect token count): $line")
                        }
                    }
                }
            }
            Log.i(TAG, "Successfully loaded ${companyList.size} companies from CSV.")
        } catch (e: IOException) {
            Log.e(TAG, "Error reading company list from CSV: ${e.message}", e)
            // Return empty list in case of I/O error (e.g., file not found)
            return@withContext emptyList<CompanySubscriptionInfo>()
        } catch (e: Exception) {
            Log.e(TAG, "An unexpected error occurred while loading company list: ${e.message}", e)
            // Return empty list for any other unexpected error
            return@withContext emptyList<CompanySubscriptionInfo>()
        }
        return@withContext companyList
    }
}
