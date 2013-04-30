package com.noisetracks.android.audio;

import java.io.*;

class DataInputStreamLittleEndian {
	private DataInputStream systemStream;

	public DataInputStreamLittleEndian(DataInputStream systemStream) {
		this.systemStream = systemStream;
	}

	public void close() throws IOException {
		this.systemStream.close();
	}

	public void read(byte[] byteBufferToReadInto) throws IOException {
		// no need to translate to little-endian here

		this.systemStream.read(byteBufferToReadInto);
	}

	public int readInt() throws IOException {
		byte[] bytesLittleEndian = new byte[4];
		this.systemStream.read(bytesLittleEndian);

		long returnValueAsLong = ((bytesLittleEndian[0] & 0xFF)
				| ((bytesLittleEndian[1] & 0xFF) << 8)
				| ((bytesLittleEndian[2] & 0xFF) << 16) | ((bytesLittleEndian[3] & 0xFF) << 24));

		return (int) returnValueAsLong;
	}

	public short readShort() throws IOException {
		byte[] bytesLittleEndian = new byte[2];
		this.systemStream.read(bytesLittleEndian);

		int returnValueAsInt = ((bytesLittleEndian[0] & 0xFF) | ((bytesLittleEndian[1] & 0xFF) << 8));

		return (short) returnValueAsInt;
	}
}

class DataOutputStreamLittleEndian {
	private DataOutputStream systemStream;

	public DataOutputStreamLittleEndian(DataOutputStream systemStream) {
		this.systemStream = systemStream;
	}

	public void close() throws IOException {
		this.systemStream.close();
	}

	public void writeString(String stringToWrite) throws IOException {
		this.systemStream.writeBytes(stringToWrite);
	}

	public void writeBytes(byte[] bytesToWrite) throws IOException {
		this.systemStream.write(bytesToWrite, 0, bytesToWrite.length);
	}

	public void writeInt(int intToWrite) throws IOException {
		byte[] intToWriteAsBytesLittleEndian = new byte[] {
				(byte) (intToWrite & 0xFF), (byte) ((intToWrite >> 8) & 0xFF),
				(byte) ((intToWrite >> 16) & 0xFF),
				(byte) ((intToWrite >> 24) & 0xFF), };

		this.systemStream.write(intToWriteAsBytesLittleEndian, 0, 4);
	}

	public void writeShort(short shortToWrite) throws IOException {
		byte[] shortToWriteAsBytesLittleEndian = new byte[] {
				(byte) shortToWrite, (byte) (shortToWrite >>> 8 & 0xFF), };

		this.systemStream.write(shortToWriteAsBytesLittleEndian, 0, 2);
	}
}

public class WavFile {
	public static final int BitsPerByte = 8;
	public static final int NumberOfBytesInRiffWaveAndFormatChunks = 36;

	public String filePath;
	public SamplingInfo samplingInfo;
	public Sample[][] samplesForChannels;

	public WavFile(String filePath, SamplingInfo samplingInfo,
			Sample[][] samplesForChannels) {
		this.filePath = filePath;
		this.samplingInfo = samplingInfo;
		this.samplesForChannels = samplesForChannels;
	}

	public static WavFile readFromFilePath(String filePathToReadFrom) {
		WavFile returnValue = new WavFile(filePathToReadFrom, null, null);

		try {
			DataInputStream dataInputStream = new DataInputStream(
					new BufferedInputStream(new FileInputStream(
							filePathToReadFrom)));

			DataInputStreamLittleEndian reader;
			reader = new DataInputStreamLittleEndian(dataInputStream);

			returnValue.readFromFilePath_ReadChunks(reader);

			reader.close();
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		return returnValue;
	}

	private void readFromFilePath_ReadChunks(DataInputStreamLittleEndian reader)
			throws IOException {
		byte[] riff = new byte[4];
		reader.read(riff);

		int numberOfBytesInFile = reader.readInt();

		byte[] wave = new byte[4];
		reader.read(wave);

		this.readFromFilePath_ReadChunks_Format(reader);
		this.readFromFilePath_ReadChunks_Data(reader);
	}

	private void readFromFilePath_ReadChunks_Format(
			DataInputStreamLittleEndian reader) throws IOException {
		byte[] fmt = new byte[4];
		reader.read(fmt);
		int chunkSizeInBytes = reader.readInt();
		Short formatCode = reader.readShort();

		Short numberOfChannels = reader.readShort();
		int samplesPerSecond = reader.readInt();
		int bytesPerSecond = reader.readInt();
		Short bytesPerSampleMaybe = reader.readShort();
		Short bitsPerSample = reader.readShort();

		SamplingInfo samplingInfo = new SamplingInfo("[from file]",
				chunkSizeInBytes, formatCode, numberOfChannels,
				samplesPerSecond, bitsPerSample);

		this.samplingInfo = samplingInfo;
	}

	private void readFromFilePath_ReadChunks_Data(
			DataInputStreamLittleEndian reader) throws IOException {
		byte[] data = new byte[4];
		reader.read(data);
		int subchunk2Size = reader.readInt();

		byte[] samplesForChannelsMixedAsBytes = new byte[subchunk2Size];
		reader.read(samplesForChannelsMixedAsBytes);

		Sample[][] samplesForChannels = Sample.buildManyFromBytes(samplingInfo,
				samplesForChannelsMixedAsBytes);

		this.samplesForChannels = samplesForChannels;
	}

	public void writeToFilePath() {
		try {
			DataOutputStream dataOutputStream = new DataOutputStream(
					new BufferedOutputStream(
							new FileOutputStream(this.filePath)));

			DataOutputStreamLittleEndian writer;
			writer = new DataOutputStreamLittleEndian(dataOutputStream);

			this.writeToFilePath_WriteChunks(writer);

			writer.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void writeToFilePath_WriteChunks(DataOutputStreamLittleEndian writer)
			throws IOException {
		int numberOfBytesInSamples = (int) (this.samplesForChannels[0].length
				* this.samplingInfo.numberOfChannels
				* this.samplingInfo.bitsPerSample / WavFile.BitsPerByte);

		writer.writeString("RIFF");
		writer.writeInt((int) (numberOfBytesInSamples + WavFile.NumberOfBytesInRiffWaveAndFormatChunks));
		writer.writeString("WAVE");

		this.writeToFilePath_WriteChunks_Format(writer);
		this.writeToFilePath_WriteChunks_Data(writer);
	}

	private void writeToFilePath_WriteChunks_Format(
			DataOutputStreamLittleEndian writer) throws IOException {
		writer.writeString("fmt ");
		writer.writeInt(this.samplingInfo.chunkSizeInBytes);
		writer.writeShort(this.samplingInfo.formatCode);
		writer.writeShort((short) this.samplingInfo.numberOfChannels);
		writer.writeInt((int) this.samplingInfo.samplesPerSecond);
		writer.writeInt((int) this.samplingInfo.bytesPerSecond());
		writer.writeShort((short) (this.samplingInfo.numberOfChannels
				* this.samplingInfo.bitsPerSample / WavFile.BitsPerByte));
		writer.writeShort((short) this.samplingInfo.bitsPerSample);
	}

	private void writeToFilePath_WriteChunks_Data(
			DataOutputStreamLittleEndian writer) throws IOException {
		writer.writeString("data");

		int numberOfBytesInSamples = (int) (this.samplesForChannels[0].length
				* this.samplingInfo.numberOfChannels
				* this.samplingInfo.bitsPerSample / WavFile.BitsPerByte);

		writer.writeInt(numberOfBytesInSamples);

		byte[] samplesAsBytes = Sample.convertManyToBytes(
				this.samplesForChannels, this.samplingInfo);

		writer.writeBytes(samplesAsBytes);
	}

	// inner classes

	public static abstract class Sample {
		public abstract Sample buildFromBytes(byte[] valueAsBytes);

		public abstract Sample buildFromDouble(double valueAsDouble);

		public abstract byte[] convertToBytes();

		public abstract double convertToDouble();

		public static Sample[][] buildManyFromBytes(SamplingInfo samplingInfo,
				byte[] bytesToConvert) {
			int numberOfBytes = bytesToConvert.length;

			int numberOfChannels = samplingInfo.numberOfChannels;

			Sample[][] returnSamples = new Sample[numberOfChannels][];

			int bytesPerSample = samplingInfo.bitsPerSample
					/ WavFile.BitsPerByte;

			int samplesPerChannel = numberOfBytes / bytesPerSample
					/ numberOfChannels;

			for (int c = 0; c < numberOfChannels; c++) {
				returnSamples[c] = new Sample[samplesPerChannel];
			}

			int b = 0;

			double halfMaxValueForEachSample = Math.pow(2, WavFile.BitsPerByte
					* bytesPerSample - 1);

			Sample samplePrototype = samplingInfo.samplePrototype();

			byte[] sampleValueAsBytes = new byte[bytesPerSample];

			for (int s = 0; s < samplesPerChannel; s++) {
				for (int c = 0; c < numberOfChannels; c++) {
					for (int i = 0; i < bytesPerSample; i++) {
						sampleValueAsBytes[i] = bytesToConvert[b];
						b++;
					}

					returnSamples[c][s] = samplePrototype
							.buildFromBytes(sampleValueAsBytes);
				}
			}

			return returnSamples;
		}

		public static Sample[] concatenateSets(Sample[][] setsToConcatenate) {
			int numberOfSamplesSoFar = 0;

			for (int i = 0; i < setsToConcatenate.length; i++) {
				Sample[] setToConcatenate = setsToConcatenate[i];
				numberOfSamplesSoFar += setToConcatenate.length;
			}

			Sample[] returnValues = new Sample[numberOfSamplesSoFar];

			int s = 0;

			for (int i = 0; i < setsToConcatenate.length; i++) {
				Sample[] setToConcatenate = setsToConcatenate[i];

				for (int j = 0; j < setToConcatenate.length; j++) {
					returnValues[s] = setToConcatenate[j];
					s++;
				}
			}

			return returnValues;
		}

		public static byte[] convertManyToBytes(Sample[][] samplesToConvert,
				SamplingInfo samplingInfo) {
			byte[] returnBytes = null;

			int numberOfChannels = samplingInfo.numberOfChannels;

			int samplesPerChannel = samplesToConvert[0].length;

			int bitsPerSample = samplingInfo.bitsPerSample;

			int bytesPerSample = bitsPerSample / WavFile.BitsPerByte;

			int numberOfBytes = numberOfChannels * samplesPerChannel
					* bytesPerSample;

			returnBytes = new byte[numberOfBytes];

			double halfMaxValueForEachSample = Math.pow(2, WavFile.BitsPerByte
					* bytesPerSample - 1);

			int b = 0;

			for (int s = 0; s < samplesPerChannel; s++) {
				for (int c = 0; c < numberOfChannels; c++) {
					Sample sample = samplesToConvert[c][s];

					byte[] sampleAsBytes = sample.convertToBytes();

					for (int i = 0; i < bytesPerSample; i++) {
						returnBytes[b] = sampleAsBytes[i];
						b++;
					}
				}
			}

			return returnBytes;
		}

		public static Sample[] superimposeSets(Sample[][] setsToSuperimpose) {
			int maxSamplesSoFar = 0;

			for (int i = 0; i < setsToSuperimpose.length; i++) {
				Sample[] setToSuperimpose = setsToSuperimpose[i];

				if (setToSuperimpose.length > maxSamplesSoFar) {
					maxSamplesSoFar = setToSuperimpose.length;
				}
			}

			Sample[] returnValues = new Sample[maxSamplesSoFar];

			for (int i = 0; i < setsToSuperimpose.length; i++) {
				Sample[] setToSuperimpose = setsToSuperimpose[i];

				for (int j = 0; j < setToSuperimpose.length; j++) {
					Sample sampleToSuperimpose = setToSuperimpose[j];

					double sampleValueAsDouble = sampleToSuperimpose
							.convertToDouble();

					if (i > 0) {
						sampleValueAsDouble += returnValues[i]
								.convertToDouble();
					}

					returnValues[i] = sampleToSuperimpose
							.buildFromDouble(sampleValueAsDouble);
				}
			}

			return returnValues;
		}
	}

	public static class Sample16 extends Sample {
		public short value;

		public Sample16(short value) {
			this.value = value;
		}

		// Sample members

		public Sample buildFromBytes(byte[] valueAsBytes) {
			short valueAsShort = (short) (((valueAsBytes[0] & 0xFF)) | (short) ((valueAsBytes[1] & 0xFF) << 8));

			return new Sample16(valueAsShort);
		}

		public Sample buildFromDouble(double valueAsDouble) {
			return new Sample16((short) (valueAsDouble * Short.MAX_VALUE));
		}

		public byte[] convertToBytes() {
			return new byte[] { (byte) ((this.value) & 0xFF),
					(byte) ((this.value >>> 8) & 0xFF), };
		}

		public double convertToDouble() {
			return this.value / Short.MAX_VALUE;
		}
	}

	public static class Sample24 extends Sample {
		public static int MAX_VALUE = (int) Math.pow(2, 23);

		public int value;

		public Sample24(int value) {
			this.value = value;
		}

		// Sample members

		public Sample buildFromBytes(byte[] valueAsBytes) {
			short valueAsShort = (short) (((valueAsBytes[0] & 0xFF))
					| ((valueAsBytes[1] & 0xFF) << 8) | ((valueAsBytes[2] & 0xFF) << 16));

			return new Sample24(valueAsShort);
		}

		public Sample buildFromDouble(double valueAsDouble) {
			return new Sample24((int) (valueAsDouble * Integer.MAX_VALUE));
		}

		public byte[] convertToBytes() {
			return new byte[] { (byte) ((this.value) & 0xFF),
					(byte) ((this.value >>> 8) & 0xFF),
					(byte) ((this.value >>> 16) & 0xFF), };
		}

		public double convertToDouble() {
			return this.value / MAX_VALUE;
		}
	}

	public static class Sample32 extends Sample {
		public int value;

		public Sample32(int value) {
			this.value = value;
		}

		// Sample members

		public Sample addInPlace(Sample other) {
			this.value += ((Sample16) other).value;
			return this;
		}

		public Sample buildFromBytes(byte[] valueAsBytes) {
			short valueAsShort = (short) (((valueAsBytes[0] & 0xFF))
					| ((valueAsBytes[1] & 0xFF) << 8)
					| ((valueAsBytes[2] & 0xFF) << 16) | ((valueAsBytes[3] & 0xFF) << 24));

			return new Sample32(valueAsShort);
		}

		public Sample buildFromDouble(double valueAsDouble) {
			return new Sample32((int) (valueAsDouble * Integer.MAX_VALUE));
		}

		public byte[] convertToBytes() {
			return new byte[] { (byte) ((this.value) & 0xFF),
					(byte) ((this.value >>> 8) & 0xFF),
					(byte) ((this.value >>> 16) & 0xFF),
					(byte) ((this.value >>> 24) & 0xFF), };
		}

		public double convertToDouble() {
			return this.value / Integer.MAX_VALUE;
		}
	}

	public static class SamplingInfo {
		public String name;
		public int chunkSizeInBytes;
		public short formatCode;
		public short numberOfChannels;
		public int samplesPerSecond;
		public short bitsPerSample;

		public SamplingInfo(String name, int chunkSizeInBytes,
				short formatCode, short numberOfChannels, int samplesPerSecond,
				short bitsPerSample) {
			this.name = name;
			this.chunkSizeInBytes = chunkSizeInBytes;
			this.formatCode = formatCode;
			this.numberOfChannels = numberOfChannels;
			this.samplesPerSecond = samplesPerSecond;
			this.bitsPerSample = bitsPerSample;
		}

		public static class Instances {
			public static SamplingInfo Default = new SamplingInfo("Default",
					16, // chunkSizeInBytes
					(short) 1, // formatCode
					(short) 1, // numberOfChannels
					44100, // samplesPerSecond
					(short) 16 // bitsPerSample
			);
		}

		public int bytesPerSecond() {
			return this.samplesPerSecond * this.numberOfChannels
					* this.bitsPerSample / WavFile.BitsPerByte;
		}

		public Sample samplePrototype() {
			Sample returnValue = null;

			if (this.bitsPerSample == 16) {
				returnValue = new WavFile.Sample16((short) 0);
			} else if (this.bitsPerSample == 24) {
				returnValue = new WavFile.Sample24(0);
			} else if (this.bitsPerSample == 32) {
				returnValue = new WavFile.Sample32(0);
			}

			return returnValue;
		}

		public String toString() {
			String returnValue = "<SamplingInfo " + "chunkSizeInBytes='"
					+ this.chunkSizeInBytes + "' " + "formatCode='"
					+ this.formatCode + "' " + "numberOfChannels='"
					+ this.numberOfChannels + "' " + "samplesPerSecond='"
					+ this.samplesPerSecond + "' " + "bitsPerSample='"
					+ this.bitsPerSample + "' " + "/>";

			return returnValue;
		}
	}
}