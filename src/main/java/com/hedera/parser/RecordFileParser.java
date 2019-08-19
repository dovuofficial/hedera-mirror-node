
package com.hedera.parser;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.protobuf.TextFormat;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.configLoader.ConfigLoader.OPERATION_TYPE;
import com.hedera.recordFileLogger.LoggerStatus;
import com.hedera.recordFileLogger.RecordFileLogger;
import com.hedera.recordFileLogger.RecordFileLogger.INIT_RESULT;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;


/**
 * This is a utility file to read back service record file generated by Hedera node
 */
public class RecordFileParser {

	private static final Logger log = LogManager.getLogger("recordfileparser");
	private static final Marker MARKER = MarkerManager.getMarker("SERVICE_RECORD");
	static final Marker LOGM_EXCEPTION = MarkerManager.getMarker("EXCEPTION");

	static final byte TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 or previous files
	static final byte TYPE_RECORD = 2;          // next data type is transaction and its record
	static final byte TYPE_SIGNATURE = 3;       // the file content signature, should not be hashed

	private static LoggerStatus loggerStatus = new LoggerStatus();

	private static Instant timeStart;
	private static String nowTime() {
		System.out.println(Instant.now().minusSeconds(timeStart.getEpochSecond()).minusNanos(timeStart.getNano()));
		return Instant.now().getEpochSecond() * 1000000000 + Instant.now().getNano() - timeStart.getEpochSecond() * 1000000000 + timeStart.getNano() + "-";
				
	}
	/**
	 * Given a service record name, read and parse and return as a list of service record pair
	 *
	 * @param fileName
	 * 		the name of record file to read
	 * @return return previous file hash
	 */
	static public boolean loadRecordFile(String fileName, String previousFileHash, String thisFileHash) {

		timeStart = Instant.now();
		
		System.out.println(nowTime() + "-LoadRecordFile start ");
		File file = new File(fileName);
		FileInputStream stream = null;
		String newFileHash = "";

		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + fileName);
			return false;
		}
		long counter = 0;
		byte[] readFileHash = new byte[48];
		INIT_RESULT initFileResult = RecordFileLogger.initFile(fileName);
		if ((initFileResult == INIT_RESULT.OK) || (initFileResult == INIT_RESULT.SKIP)) {
			try {
				stream = new FileInputStream(file);
				DataInputStream dis = new DataInputStream(stream);

				int record_format_version = dis.readInt();
				int version = dis.readInt();

				log.info(MARKER, "Record file format version " + record_format_version);
				log.info(MARKER, "HAPI protocol version " + version);

				while (dis.available() != 0) {

					try {
						byte typeDelimiter = dis.readByte();

						switch (typeDelimiter) {
							case TYPE_PREV_HASH:
								dis.read(readFileHash);
								if (Utility.hashIsEmpty(previousFileHash)) {
									log.error(MARKER, "Previous file Hash not available");
									previousFileHash = Hex.encodeHexString(readFileHash);
								} else {
									log.info(MARKER, "Previous file Hash = " + previousFileHash);
								}
								newFileHash = Hex.encodeHexString(readFileHash);
								log.info(MARKER, "New file Hash = " + newFileHash);

								if (!newFileHash.contentEquals(previousFileHash)) {
									
									if (ConfigLoader.getStopLoggingIfRecordHashMismatchAfter().compareTo(Utility.getFileName(fileName)) < 0) {
										// last file for which mismatch is allowed is in the past
										log.error(MARKER, "Previous file Hash Mismatch - stopping loading. Previous = {}, Current = {}", previousFileHash, newFileHash);
										log.error(MARKER, "Mismatching file {}", fileName);
										return false;
									}
								}
								break;
							case TYPE_RECORD:
								counter++;

								int byteLength = dis.readInt();
								byte[] rawBytes = new byte[byteLength];
								dis.readFully(rawBytes);
								Transaction transaction = Transaction.parseFrom(rawBytes);

								byteLength = dis.readInt();
								rawBytes = new byte[byteLength];
								dis.readFully(rawBytes);
								TransactionRecord txRecord = TransactionRecord.parseFrom(rawBytes);

								if (initFileResult != INIT_RESULT.SKIP) {
									boolean bStored = RecordFileLogger.storeRecord(counter, Utility.convertToInstant(txRecord.getConsensusTimestamp()), transaction, txRecord);
									if (bStored) {
										log.info(MARKER, "record counter = {}\n=============================", counter);
										log.info(MARKER, "Transaction Consensus Timestamp = {}\n", Utility.convertToInstant(txRecord.getConsensusTimestamp()));
										log.info(MARKER, "Transaction = {}", Utility.printTransaction(transaction));
										log.info(MARKER, "Record = {}\n=============================\n",  TextFormat.shortDebugString(txRecord));
									} else {
										RecordFileLogger.rollback();
										return false;
									}
								}
								break;
							case TYPE_SIGNATURE:
								int sigLength = dis.readInt();
								log.info(MARKER, "sigLength = " + sigLength);
								byte[] sigBytes = new byte[sigLength];
								dis.readFully(sigBytes);
								log.info(MARKER, "File {} Signature = {} ", fileName, Hex.encodeHexString(sigBytes));
								if (RecordFileLogger.storeSignature(Hex.encodeHexString(sigBytes))) {
									break;
								} else {
									return false;
								}

							default:
								log.error(LOGM_EXCEPTION, "Exception Unknown record file delimiter {}", typeDelimiter);
						}


					} catch (Exception e) {
						log.error(LOGM_EXCEPTION, "Exception {}", e);
						RecordFileLogger.rollback();
						dis.close();
						return false;
					}
				}
				dis.close();
				RecordFileLogger.completeFile(thisFileHash, previousFileHash);
			} catch (FileNotFoundException e) {
				log.error(MARKER, "File Not Found Error {}", e);
				return false;
			} catch (IOException e) {
				log.error(MARKER, "IOException Error {}", e);
				return false;
			} catch (Exception e) {
				log.error(MARKER, "Parsing Error {}", e);
				return false;
			} finally {
				try {
					if (stream != null)
						stream.close();
				} catch (IOException ex) {
					log.error("Exception in close the stream {}", ex);
				}
			}
			loggerStatus.setLastProcessedRcdHash(newFileHash);
			loggerStatus.saveToFile();
			System.out.println(counter);
			System.out.println(nowTime() + "-LoadRecordFile end ");
			return true;
		} else if (initFileResult == INIT_RESULT.SKIP) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * read and parse a list of record files
	 */
	static public void loadRecordFiles(List<String> fileNames) {

		String prevFileHash = loggerStatus.getLastProcessedRcdHash();

		for (String name : fileNames) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, stopping.");
				return;
			}
			String thisFileHash = Utility.bytesToHex(Utility.getFileHash(name));
			if (loadRecordFile(name, prevFileHash, thisFileHash)) {
				prevFileHash = thisFileHash;
				Utility.moveFileToParsedDir(name, "/parsedRecordFiles/");
			} else {
				return;
			}
		}
	}

	public static void parseNewFiles(String pathName) {
		if (RecordFileLogger.start()) {

			File file = new File(pathName);
			if ( ! file.exists()) {
				file.mkdirs();
			}
			
			if (file.isDirectory()) { //if it's a directory

				String[] files = file.list(); // get all files under the directory
				Arrays.sort(files);           // sorted by name (timestamp)

				// add directory prefix to get full path
				List<String> fullPaths = Arrays.asList(files).stream()
						.filter(f -> Utility.isRecordFile(f))
						.map(s -> file + "/" + s)
						.collect(Collectors.toList());

				log.info(MARKER, "Loading record files from directory {} ", pathName);

				if (fullPaths != null) {
					log.info(MARKER, "Files are " + fullPaths);
					loadRecordFiles(fullPaths);
				} else {
					log.info(MARKER, "No files to parse");
				}
			} else {
				log.error(LOGM_EXCEPTION, "Input parameter {} is not a folder", pathName);

			}
			RecordFileLogger.finish();
		}
	}

	public static void main(String[] args) {
		String pathName;

		while (true) {
			if (Utility.checkStopFile()) {
				log.info(MARKER, "Stop file found, exiting.");
				System.exit(0);
			}

			pathName = ConfigLoader.getDefaultParseDir(OPERATION_TYPE.RECORDS);
			log.info(MARKER, "Record files folder got from configuration file: {}", pathName);

			if (pathName != null) {
				parseNewFiles(pathName);
			}
		}
	}

	/**
	 * Given a service record name, read its prevFileHash
	 *
	 * @param fileName
	 * 		the name of record file to read
	 * @return return previous file hash's Hex String
	 */
	static public String readPrevFileHash(String fileName) {
		File file = new File(fileName);
		FileInputStream stream = null;
		if (file.exists() == false) {
			log.info(MARKER, "File does not exist " + fileName);
			return null;
		}
		byte[] prevFileHash = new byte[48];
		try {
			stream = new FileInputStream(file);
			DataInputStream dis = new DataInputStream(stream);

			// record_format_version
			dis.readInt();
			// version
			dis.readInt();

			byte typeDelimiter = dis.readByte();

			if (typeDelimiter == TYPE_PREV_HASH) {
				dis.read(prevFileHash);
				String hexString = Hex.encodeHexString(prevFileHash);
				log.info(MARKER, "readPrevFileHash :: Previous file Hash = {}, file name = {}", hexString, fileName);
				dis.close();
				return hexString;
			} else {
				log.error(MARKER, "readPrevFileHash :: Should read Previous file Hash, but found file delimiter {}, file name = {}", typeDelimiter, fileName);
			}
			dis.close();

		} catch (FileNotFoundException e) {
			log.error(MARKER, "readPrevFileHash :: File Not Found Error, file name = {}",  fileName);
		} catch (IOException e) {
			log.error(MARKER, "readPrevFileHash :: IOException Error, file name = {}, Exception {}",  fileName, e);
		} catch (Exception e) {
			log.error(MARKER, "readPrevFileHash :: Parsing Error, file name = {}, Exception {}",  fileName, e);
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (IOException ex) {
				log.error("readPrevFileHash :: Exception in close the stream {}", ex);
			}
		}

		return null;
	}
}
