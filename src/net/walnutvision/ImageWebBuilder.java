package net.walnutvision;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.io.EndianUtils;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.HTablePool;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;

public class ImageWebBuilder {
	static class Mapper1 extends TableMapper<Text, LongWritable> {
		private HTablePool mHTablePool = new HTablePool();
		private int numRecords = 0;

		public ArrayList<Long> getImageKey(ArrayList<String> imageHashArray) {
			ArrayList<Long> imageKeyArray = new ArrayList<Long>();
			String tableName = "image";
			HTable imageTable = (HTable) mHTablePool.getTable(tableName);
			byte[] family = Bytes.toBytes("d");
			byte[] idColumn = Bytes.toBytes("id_0");
			for (String imageHash : imageHashArray) {
				Get get = new Get(Bytes.toBytes(imageHash));
				get.addColumn(family, idColumn);
				Result result;
				try {
					result = imageTable.get(get);
					byte[] rowKey = result.getValue(family, idColumn);
					long imageKey = EndianUtils.readSwappedLong(rowKey, 0);
					imageKeyArray.add(imageKey);
				} catch (IOException e) {
				}
			}
			try {
				mHTablePool.closeTablePool(tableName);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return imageKeyArray;
		}

		public void map(ImmutableBytesWritable row, Result values,
				Context context) throws IOException {
			numRecords++;
			if ((numRecords % 1000) == 0) {
				context.setStatus("mapper processed " + numRecords
						+ " records so far");
			}
			byte[] family = Bytes.toBytes("d");
			ArrayList<String> imageHashArray = HbaseAdapter.getColumn(values,
					family, "fii_");
			ArrayList<Long> imageKeyArray = getImageKey(imageHashArray);
			if (imageHashArray.size() != imageKeyArray.size()) {
				System.out.println("Error in imageKeyArray size");
				return;
			}
			String merchant = Bytes.toString(values.getValue(family,
					Bytes.toBytes("m_0")));
			ArrayList<String> categoryColumn = CategoryConverter
					.convertCategoryArray(merchant,
							HbaseAdapter.getColumn(values, family, "cp_"));
			if (categoryColumn.isEmpty()) {
				System.out.println("Empty categoryColumn");
				return;
			}
			// emit to reducer
			for (String oneCategory : categoryColumn) {
				for (Long imageKey : imageKeyArray) {
					try {
						if (oneCategory.startsWith("图书音像")) {
							context.write(new Text("图书音像"), new LongWritable(
									imageKey));
						} else {
							context.write(new Text(oneCategory),
									new LongWritable(imageKey));
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}

		@Override
		public void setup(Context context) {
			// initialize the category converter
			CategoryConverter.init(CategoryDirectory);
		}

		@Override
		public void cleanup(Context context) {

		}
	}

	public static class Reducer1 extends
			TableReducer<Text, LongWritable, ImmutableBytesWritable> {
		private HTablePool mHTablePool = new HTablePool();

		public int getUniqueId() {
			String tableName = "category_index";
			HTable categoryTable = (HTable) mHTablePool.getTable(tableName);
			byte[] family = Bytes.toBytes("d");
			byte[] id = Bytes.toBytes("id");
			byte[] rowKey = Bytes.toBytes("unique_id");
			long nextId = -1;
			try {
				nextId = categoryTable.incrementColumnValue(rowKey, family, id,
						1);
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				mHTablePool.closeTablePool(tableName);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return (int) nextId;
		}

		@Override
		public void reduce(Text key, Iterable<LongWritable> values,
				Context context) throws IOException, InterruptedException {
			int colorId = getUniqueId();
			int shapeId = getUniqueId();
			int surfId = getUniqueId();
			int buildId = getUniqueId();
			if (colorId != -1 && shapeId != -1 && surfId != -1 && buildId != -1) {
				String tableName = "category_index";
				HTable categoryTable = (HTable) mHTablePool.getTable(tableName);
				byte[] family = Bytes.toBytes("d");
				int columnSize = 100000;
				int columnIndex = 0;
				int i = 0;
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				for (LongWritable value : values) {
					++i;
					EndianUtils.writeSwappedLong(outputStream, value.get());
					if (i % columnSize == 0) {
						Put put = new Put(Bytes.toBytes(key.toString()));
						put.add(family, Bytes.toBytes("" + columnIndex),
								outputStream.toByteArray());
						categoryTable.put(put);
						++columnIndex;
						outputStream.reset();
					}
				}
				if (i % columnSize != 0) {
					Put put = new Put(Bytes.toBytes(key.toString()));
					put.add(family, Bytes.toBytes("" + columnIndex),
							outputStream.toByteArray());
					categoryTable.put(put);
					++columnIndex;
				}
				// add treeIndex to the row
				Put put = new Put(Bytes.toBytes(key.toString()));
				put.add(family, Bytes.toBytes("color"),
						Bytes.toBytes(EndianUtils.swapInteger(colorId)));
				put.add(family, Bytes.toBytes("shape"),
						Bytes.toBytes(EndianUtils.swapInteger(shapeId)));
				put.add(family, Bytes.toBytes("surf"),
						Bytes.toBytes(EndianUtils.swapInteger(surfId)));
				put.add(family, Bytes.toBytes("id"),
						Bytes.toBytes(EndianUtils.swapInteger(buildId)));
				categoryTable.put(put);
				mHTablePool.closeTablePool(tableName);
			}
		}

		@Override
		public void setup(Context context) {

		}

		@Override
		public void cleanup(Context context) {

		}
	}

	public static void main(String[] args) throws Exception {
		HBaseConfiguration conf = new HBaseConfiguration();
		Job job = new Job(conf, "ImageWebBuilder");
		job.setJarByClass(ImageWebBuilder.class);
		Scan scan = new Scan();
		TableMapReduceUtil.initTableMapperJob("test_item", scan, Mapper1.class,
				Text.class, LongWritable.class, job);
		TableMapReduceUtil
				.initTableReducerJob("test_item", Reducer1.class, job);
		boolean success = job.waitForCompletion(true);
//		TTransport mANNTreeTransport = null;
//		TProtocol mANNTreeProtocol = null;
//		ANNTreeDaemon.Client mANNTreeDaemonClient = null;
//		mANNTreeTransport = new TFramedTransport(new TSocket(
//				ANNTreeDaemonServerAddress, ANNTreeDaemonServerPort));
//		mANNTreeProtocol = new TBinaryProtocol(mANNTreeTransport);
//		mANNTreeDaemonClient = new ANNTreeDaemon.Client(mANNTreeProtocol);
//		try {
//			mANNTreeTransport.open();
//			mANNTreeDaemonClient.buildAllCategory();
//		} catch (TTransportException e) {
//			e.printStackTrace();
//		} catch (TException e) {
//			e.printStackTrace();
//		}
//		mANNTreeTransport.close();
		System.exit(success ? 0 : 1);
	}

	public static String CategoryDirectory = "/export/walnut/workspace/category";
	public static String ANNTreeDaemonServerAddress = "node1";
	public static int ANNTreeDaemonServerPort = 9999;

}
