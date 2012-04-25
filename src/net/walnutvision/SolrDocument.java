package net.walnutvision;

import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.apache.commons.io.EndianUtils;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.HTablePool;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.solr.common.SolrInputDocument;

public class SolrDocument {

	public SolrDocument() {
		mDoc = new SolrInputDocument();
		mFamily = Bytes.toBytes("d");
	}

	public void parse(String rowKey) throws IOException {
		HTablePool hTablePool = new HTablePool();
		String tableName = "test_item";
		HTable table = (HTable) hTablePool.getTable(tableName);
		Get get = new Get(Bytes.toBytes(rowKey));
		get.addFamily(mFamily);
		Result result = table.get(get);
		parseRow(result);
		hTablePool.closeTablePool(tableName);
	}

	public SolrInputDocument getDocument() {
		return mDoc;
	}

	private ArrayList<String> buildHierarchyCategory(ArrayList<String> category) {
		ArrayList<String> ret = new ArrayList<String>();
		int levelCount = 0;
		String current = "";
		for (String token : category) {
			current += "|" + token;
			String facet = "" + levelCount + current;
			ret.add(facet);
			++levelCount;
		}
		return ret;
	}

	private void addCategory(ArrayList<String> categoryArray) {
		for (String category : categoryArray) {
			ArrayList<String> tokenArray = new ArrayList<String>();
			StringTokenizer tokenizer = new StringTokenizer(category, "|");
			while (tokenizer.hasMoreTokens()) {
				tokenArray.add(tokenizer.nextToken());
			}
			ArrayList<String> hierarchicalTokenArray = buildHierarchyCategory(tokenArray);
			for (String hierarchicalToken : hierarchicalTokenArray) {
				addField("category", hierarchicalToken);
			}
		}
	}

	private void addImage(ArrayList<String> imageHashArray) throws IOException {
		HTablePool hTablePool = new HTablePool();
		String tableName = "image";
		HTable imageTable = (HTable) hTablePool.getTable(tableName);
		byte[] idColumn = Bytes.toBytes("id_0");
		for (String imageHash : imageHashArray) {
			Get get = new Get(Bytes.toBytes(imageHash));
			get.addColumn(mFamily, idColumn);
			Result result = imageTable.get(get);
			if (!result.isEmpty()) {
				byte[] bId = result.getValue(mFamily, idColumn);
				Long imageKey = EndianUtils.readSwappedLong(bId, 0);
				if (imageHash != null && imageKey != null) {
					addField("imagehash", imageHash);
					addField("imagekey", imageKey);
					mImagekey.add(imageKey);
				}
			}
		}
		hTablePool.closeTablePool(tableName);
	}

	private void addField(String name, Object value) {
		if (value != null) {
			mDoc.addField(name, value);
		}
	}

	private void parseRow(Result result) throws IOException {
		mId = Bytes.toString(result.getRow());
		mName = Bytes.toString(result.getValue(mFamily, Bytes.toBytes("nm_0")));
		mPrice = Float.parseFloat(Bytes.toString(
				result.getValue(mFamily, Bytes.toBytes("pr_0"))).replace(",",
				""));
		mImagehash = HbaseAdapter.getColumn(result, mFamily, "fii_");
		mImagekey = new ArrayList<Long>();
		mUrl = Bytes.toString(result.getValue(mFamily, Bytes.toBytes("u_0")));
		mBrand = Bytes
				.toString(result.getValue(mFamily, Bytes.toBytes("br_0")));
		mMerchant = Bytes.toString(result.getValue(mFamily,
				Bytes.toBytes("m_0")));
		mCategory = CategoryConverter.convertCategoryArray(mMerchant,
				HbaseAdapter.getColumn(result, mFamily, "cp_"));
		if (!mCategory.isEmpty()) {
			addField("id", mId);
			addField("name", mName);
			addField("price", mPrice);
			addField("url", mUrl);
			addField("bra", mBrand);
			addField("merchant", mMerchant);
			addCategory(mCategory);
			addImage(mImagehash);
		}
	}

	private String mId;
	private String mName;
	private String mBrand;
	private Float mPrice;
	private String mUrl;
	private ArrayList<String> mImagehash;
	private ArrayList<Long> mImagekey;
	private ArrayList<String> mCategory;
	private String mMerchant;
	private byte[] mFamily;
	SolrInputDocument mDoc;
}
