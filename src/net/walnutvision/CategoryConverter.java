package net.walnutvision;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

public class CategoryConverter {
	public static void init(String mappingDirectory) {
		// load the mapping file
		// only read files end with .map
		mMerchantCategoryMap = new HashMap<String, HashMap<String, String>>();
		FilenameFilter fileFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(".map");
			}
		};
		File mappingDirectoryFile = new File(mappingDirectory);
		File[] mappingFiles = mappingDirectoryFile.listFiles(fileFilter);
		for (File mappingFile : mappingFiles) {
			try {
				int dotIndex = mappingFile.getName().lastIndexOf(".");
				if (dotIndex != -1) {
					// get the merchant name
					String merchant = mappingFile.getName().substring(0,
							dotIndex);
					HashMap<String, String> merchantCategoryMap = new HashMap<String, String>();
					DataInputStream in = new DataInputStream(
							new FileInputStream(mappingFile));
					BufferedReader br = new BufferedReader(
							new InputStreamReader(in));
					String strLine;
					while ((strLine = br.readLine()) != null) {
						int index = strLine.indexOf("\t");
						if (index == -1) {
							continue;
						}
						String siteCategory = strLine.substring(0, index);
						String myCategory = strLine.substring(index + 1);
						// save the mapping in the row
						merchantCategoryMap.put(siteCategory, myCategory);
					}
					in.close();
					mMerchantCategoryMap.put(merchant, merchantCategoryMap);
				}
			} catch (IOException e) {
			}
		}
	}

	// This function converts each category in one merchant to the global
	// category
	public static ArrayList<String> convertCategoryArray(String merchant,
			ArrayList<String> categoryArray) {
		ArrayList<String> ret = new ArrayList<String>();
		HashMap<String, String> merchantCategoryMap = mMerchantCategoryMap
				.get(merchant);
		if (merchantCategoryMap != null) {
			for (String category : categoryArray) {
				if (merchantCategoryMap.containsKey(category)) {
					String newCategory = merchantCategoryMap.get(category);
					StringTokenizer tokenizer = new StringTokenizer(
							newCategory, "\t");
					while (tokenizer.hasMoreTokens()) {
						ret.add(tokenizer.nextToken());
					}
				}
			}
		}
		return ret;
	}

	private static HashMap<String, HashMap<String, String>> mMerchantCategoryMap = null;
}
