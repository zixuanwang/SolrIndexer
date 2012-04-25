package net.walnutvision;

import java.util.ArrayList;

import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;

public class HbaseAdapter {
	public static ArrayList<String> getColumn(Result values, byte[] family,
			String prefix) {
		ArrayList<String> ret = new ArrayList<String>();
		int i = 0;
		while (true) {
			byte[] v = values.getValue(family, Bytes.toBytes(prefix + i));
			if (v == null) {
				break;
			}
			ret.add(Bytes.toString(v));
			++i;
		}
		return ret;
	}
}
