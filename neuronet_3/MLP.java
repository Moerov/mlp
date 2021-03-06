package neuronet_3; //Should not be there

import java.util.*;
import java.io.*;

public class MLP{

	private static int[] transferFunctions;
	private static double[] rates;
	private static double[] momentum;
	
	private int numberOfLayers;
	private int numberOfInputs;
	private int numberOfOutputs;
	private double[][] inputDataEpoch;
	private double[][][] weights;
	private double[][] deltas;
	private double[][][] weightChanges;
	private double[][][] cumulativeWeightChanges;
	private List<Double> errorCurvePoints = new ArrayList<>();
	private double[][] teacher;
	private double[][] outputsByNeurons;
	
	private void setInitialWeights(int[] numberOfNeurons) {
//		- 1 because we do not have weights for input layer
		weights = new double[numberOfLayers-1][][];
		for(int i=0; i<numberOfLayers-1; i++){
//			for each layer we get particular number of neurons with number of inputs + BIAS
			weights[i] = new double[numberOfNeurons[i+1]][numberOfNeurons[i]+1];
		}
		outputsByNeurons = new double[numberOfLayers-1][];
		for(int i=0; i<numberOfLayers-1; i++){
//			for each layer we get particular number of neurons with number of inputs + BIAS
			outputsByNeurons[i] = new double[numberOfNeurons[i+1]];
		}
		deltas = new double[numberOfLayers-1][];
		for(int i=0; i<numberOfLayers-1; i++){
//			for each layer we get particular number of neurons with number of inputs + BIAS
			deltas[i] = new double[numberOfNeurons[i+1]];
		}
//		initialize weight changes with zeroes - so momentum and cumulated weights change for the first step is 0
		weightChanges = new double[numberOfLayers-1][][];
		for(int i=0; i<numberOfLayers-1; i++){
			weightChanges[i] = new double[numberOfNeurons[i+1]][numberOfNeurons[i]+1];
			for(double[] neuronWeights : weightChanges[i])
				Arrays.fill(neuronWeights, 0.0);
		}
		cumulativeWeightChanges = new double[numberOfLayers-1][][];
		for(int i=0; i<numberOfLayers-1; i++){
			cumulativeWeightChanges[i] = new double[numberOfNeurons[i+1]][numberOfNeurons[i]+1];
			for(double[] neuronWeights : cumulativeWeightChanges[i])
				Arrays.fill(neuronWeights, 0.0);
		}
	}

	public MLP(int[] numberOfNeurons, int seed){
		this.numberOfLayers = numberOfNeurons.length;
		this.numberOfInputs = numberOfNeurons[0];
		this.numberOfOutputs = numberOfNeurons[numberOfNeurons.length - 1];
		setInitialWeights(numberOfNeurons);
		initializeRandomWeights(seed);
	}

	//The second constructor which initializes weights from file
	public MLP(int[] numberOfNeurons, String fileWithWeights){
		this.numberOfLayers = numberOfNeurons.length;
		this.numberOfInputs = numberOfNeurons[0];
		this.numberOfOutputs = numberOfNeurons[numberOfNeurons.length - 1];
		setInitialWeights(numberOfNeurons);
		initializeWeightsFromFile(fileWithWeights);
	}

	private void initializeWeightsFromFile(String filename){
		try {
			File file = new File(filename);
			Scanner scanner = new Scanner(file);
			for(int i=0; i<numberOfLayers-1; i++){
			//+ 1 for BIAS
				for(int j=0; j<weights[i].length; j++){
					for(int k=0; k<weights[i][j].length; k++){
						weights[i][j][k] = scanner.nextDouble();
					}
				}
			}
			scanner.close();
		}
		catch (FileNotFoundException e) {
			System.out.println("File with weights not found!");
		}
		catch (Exception e) {
			System.out.println("Wrong dimensions in file!");
		}
	}

	//Initialize all weights using a seed for random generator
	private void initializeRandomWeights(int seed){
		Random rand = new Random(seed);
		//weights = new double[numberOfLayers-1][numberOfNeurons][numberOfNeurons+1];
		double rangeMin = -2.0;
		double rangeMax = 2.0;
		for (int i=0; i<numberOfLayers-1; i++){
			for (int j=0; j<weights[i].length; j++){
				for (int k=0; k<weights[i][j].length; k++){
					weights[i][j][k] = rangeMin + (rangeMax - rangeMin) * rand.nextDouble();
				}				
			}
		}
	}

	//Transfer function given an input and number of layer
	private double transfer(double net, int layerNumber){
		switch (transferFunctions[layerNumber]){
			case 0: return net;
			case 1: return 1/(1 + Math.exp(-net));
			case 2: return (Math.exp(net) - Math.exp(-net))/(Math.exp(net) + Math.exp(-net));
			default: return -42;
		}
	}
	
	private double deriviative(int layerNumber, int neuronNumber) {
		switch (transferFunctions[layerNumber]){
			case 0: return 1;
			case 1: return outputsByNeurons[layerNumber][neuronNumber]*(1 - outputsByNeurons[layerNumber][neuronNumber]);
			case 2: return 1 - Math.pow(outputsByNeurons[layerNumber][neuronNumber], 2);
			default: return -42;
		}
	}

	//Given the input for the layer and the number of layer
	//returns the output of layer
	private double[] getLayerOutput(double[] inputs, int layerNumber){
//		layerNumber is always smaller than real layer index, as we count only calculative layers
		double[] outputs = new double[weights[layerNumber].length];
		for(int neuronIndex=0; neuronIndex < outputs.length; neuronIndex++){
			double net = 1*weights[layerNumber][neuronIndex][0]; //BIAS
			for (int j = 1; j < weights[layerNumber][neuronIndex].length; j++){
				net += inputs[j-1] * weights[layerNumber][neuronIndex][j];
			}
			outputs[neuronIndex] = transfer(net, layerNumber);
			outputsByNeurons[layerNumber][neuronIndex] = outputs[neuronIndex];
		}
		return outputs;
	}

	//Write all outputs to file
	private void writeToFile(double[][] outputs, String outputFilename){
		PrintWriter fout;
		try{
			fout = new PrintWriter(outputFilename);

			for(int i=0; i<outputs.length; i++){
				for(int j=0; j<outputs[i].length; j++){
					fout.printf("%f  ",outputs[i][j]);
				}
				fout.printf("\n");
			}
			fout.close();
		}
		catch (FileNotFoundException e){
			System.out.println( e.getMessage() );
		} 
	}

	//Reads file and sets an array of input values for the MLP
	private void getInputsFromFile(String inputFilename){
		try{
			BufferedReader br = new BufferedReader(new FileReader(inputFilename));
			String line;
			line = br.readLine();
			line = br.readLine();
			String stringWithNumberOfPatterns = line.replaceAll("\\s+", " ").split(" ")[1];
			int numberOfPatterns = Integer.parseInt(stringWithNumberOfPatterns.substring(2));
			inputDataEpoch = new double[numberOfPatterns][numberOfInputs];
			int j=0;
			while ((line = br.readLine()) != null) {
   				if (line.startsWith("#")){
   					continue;
   				} else {
   					String[] allValues = line.split(" ");
   					double[] row = new double[numberOfInputs];
   					for(int i=0; i<numberOfInputs; i++){
   						row[i]= Double.parseDouble(allValues[i]);
   					}
   					inputDataEpoch[j] = row;
   					j++;
   				}
   				
			}
			br.close();
		}
		catch(IOException e){
			System.out.println( e.getMessage() );
		}
	}

	//Print out the weights for each layer and each neuron
	public void printWeights(){
		for (int i=0; i<numberOfLayers-1; i++){
			System.out.println("");
			System.out.println("Weights of the layer" + String.valueOf(i+2) + ":");
			for (int j=0; j<weights[i].length; j++){
				System.out.println("Weights of the neuron" + String.valueOf(j+1) + ":");
				for (int k=0; k<weights[i][j].length; k++){
					System.out.println(weights[i][j][k]);
				}				
			}
		}
	}

	//Read input from inputFilename and 
	//saves the output of the MLP to the outputFilename
	public void runWithOutput(String outputFilename){
		double[][] netOutputs = new double[inputDataEpoch.length][numberOfOutputs];
		//Run for all layers for every output
		double[] previousOutput;
		int outputIndex = 0;
		for(double[] input : inputDataEpoch){
			netOutputs[outputIndex] = run(input);
			outputIndex++;
		}
		// Write the whole output to the file
		writeToFile(netOutputs, outputFilename);
	}
	
//	for getting teacher values from file
	private void readTeacherOutput(String inputFilename) {
		try{
			BufferedReader br = new BufferedReader(new FileReader(inputFilename));
			String line;
			line = br.readLine();
			line = br.readLine();
			String stringWithNumberOfPatterns = line.split(" ")[1];
			int numberOfPatterns = Integer.parseInt(stringWithNumberOfPatterns.substring(2));
			teacher = new double[numberOfPatterns][numberOfOutputs];
			int j=0;
			while ((line = br.readLine()) != null) {
   				if (line.startsWith("#")){
   					continue;
   				} else {
   					String[] allValues = line.replaceAll("\\s+", " ").split(" ");
   					double[] row = new double[numberOfOutputs];
   					for(int i=numberOfInputs; i<numberOfInputs + numberOfOutputs; i++){
   						row[i - numberOfInputs] = Double.parseDouble(allValues[i]);
   					}
   					teacher[j] = row;
   					j++;
   				}
   				
			}
			br.close();
		}
		catch(IOException e){
			System.out.println( e.getMessage() );
		}
	}
	
//	shuffle input arrays
	private void shuffleInputData() {
		Random rand = new Random();
		double[] temp = new double[inputDataEpoch[0].length];
		for(int i=0; i<inputDataEpoch.length; i++){
			int randIndex = rand.nextInt(inputDataEpoch.length);
			temp = inputDataEpoch[i];
			inputDataEpoch[i] = inputDataEpoch[randIndex];
			inputDataEpoch[randIndex] = temp;
		}
	}
	
//	count error by standard formula
	private double error(double[] output, double[] teacher) {
		double sum = 0.0;
		for(int i=0; i<numberOfOutputs; i++)
			sum += Math.pow(output[i] - teacher[i], 2);
		return 0.5*sum;
	}

	private void printOutErrorCurve(){
//		write down error point to file for passing to gnuplot
		PrintWriter fout;
		String filename = "learning.curve";
		try{
			fout = new PrintWriter(filename);
			for(int i=0; i<errorCurvePoints.size(); i++)
				fout.printf("%d %f ", i, errorCurvePoints.get(i));
			fout.close();
		}
		catch (FileNotFoundException e){
			System.out.println( e.getMessage() );
		} 
	}
	
	private double[] run(double[] input) {
//		go by layers and apply transferfunctions to get output
//		save outputs for each neuron
		double[] previousOutput = input;
//		start from the first hidden layer
		for(int j=0; j<numberOfLayers-1; j++){
			previousOutput = getLayerOutput(previousOutput, j);
		}
		return previousOutput;
	}
	
	private List<Double> batchLearning() {
		List<Double> errors = new ArrayList<>();
		
		int patternNumber = 0;
		for(double[] input : inputDataEpoch) {
			double[] result = run(input);
			
			int neuronIndex = 0, weightIndex = 0;
			for(int layerIndex = numberOfLayers-2; layerIndex>=0; layerIndex-- ) {
				for(double[] neuron : weights[layerIndex]) {
					double delta = delta(patternNumber, layerIndex, neuronIndex);
					for(double weight : neuron) {
						weightChanges[layerIndex][neuronIndex][weightIndex] = 
								weightChange(layerIndex, neuronIndex, weightIndex, delta);
						cumulativeWeightChanges[layerIndex][neuronIndex][weightIndex] += 
								weightChanges[layerIndex][neuronIndex][weightIndex];
						weightIndex ++;
					}
					neuronIndex ++;
					weightIndex = 0;
				}
				layerIndex ++;
				neuronIndex = 0;
			}
			errors.add(error(result, teacher[patternNumber]));
			patternNumber ++;
		}
		applyCumulativeWeightChanges();
		
		return errors;
	}

	private double singleStepLearning(double[] input, int patternNumber) {
		double[] result = run(input);
		
		int neuronIndex = 0, weightIndex = 0;
		for(int layerIndex = numberOfLayers-2; layerIndex>=0; layerIndex-- ) {
			for(double[] neuron : weights[layerIndex]) {
				double delta = delta(patternNumber, layerIndex, neuronIndex);
				for(double weight : neuron) {
					weightChanges[layerIndex][neuronIndex][weightIndex] = 
							weightChange(layerIndex, neuronIndex, weightIndex, delta);
					weightIndex ++;
				}
				neuronIndex ++;
				weightIndex = 0;
			}
			neuronIndex = 0;
		}
		applyWeightChanges();
		
		return error(result, teacher[patternNumber]);
	}
	
	private void applyWeightChanges() {
		int layerIndex = 0, neuronIndex = 0, weightIndex = 0;
		for(double[][] layer : weights) {
			for(double[] neuron : layer) {
				for(double weight : neuron) {
					weights[layerIndex][neuronIndex][weightIndex] += weightChanges[layerIndex][neuronIndex][weightIndex];
					weightIndex ++;
				}
				neuronIndex ++;
				weightIndex = 0;
			}
			layerIndex ++;
			neuronIndex = 0;
		}
	}
	
	private void applyCumulativeWeightChanges() {
		int layerIndex = 0, neuronIndex = 0, weightIndex = 0;
		for(double[][] layer : weights) {
			for(double[] neuron : layer) {
				for(double weight : neuron) {
					weights[layerIndex][neuronIndex][weightIndex] += cumulativeWeightChanges[layerIndex][neuronIndex][weightIndex];
					weightIndex ++;
				}
				neuronIndex ++;
				weightIndex = 0;
			}
			layerIndex ++;
			neuronIndex = 0;
		}
	}
	
	private double delta(int patternNumber, int layer, int neuron) {
		double delta = 0;
//		last layer, because we save deltas only for "computative" layers
		if(layer == numberOfLayers - 2) {
			delta = (teacher[patternNumber][neuron] - outputsByNeurons[layer][neuron])*deriviative(layer, neuron);
		}
		else {
			double sumDeltas = 0.0;
			double[] belowLayerDeltas = deltas[layer + 1];
			int belowLayerNeuron = 0;
			for(double belowDelta : belowLayerDeltas) {
				sumDeltas += belowDelta * weights[layer+1][belowLayerNeuron][neuron];
				belowLayerNeuron++;
			}
			delta = sumDeltas * deriviative(layer, neuron);
		}
		deltas[layer][neuron] = delta;
		return delta;
	}
	
	private double weightChange(int layer, int neuron, int weight, double delta) {
		return rates[layer]*delta*outputsByNeurons[layer][neuron] + 
			momentum[layer]*weightChanges[layer][neuron][weight];
	}
	
	public static void main(String[] args){
		
		/******************************
		Set parameters here
		******************************/
		//Number of neurons in each layer
		int[] configuration = {4, 2, 2};
		
		//rates of learning for every hidden performing calculations layer
		double[] userRates = {0.1, 0.1};
		rates = userRates;

		//momentum for every hidden performing calculations layer
		double[] userMomentum = {0.2, 0.2};
		momentum = userMomentum;
		
		//Seed for random generator
		int seed = 42;
		
		// Trnsfer functions for each layer:
		//0 - identity, 1 - tahn, 2 - logistic
		int[] userTransferFunctions = {1, 2};
		transferFunctions = userTransferFunctions;
		
		//Should always be true:
		//configuration.length == transferFunctions.length + 1
		String inputFilename = "training.dat";
		String outputFilename = "output.dat";

		/******************************
		Set parameters here
		******************************/

//		check if the arrays length are correct
		if(configuration.length != rates.length + 1 || 
				configuration.length != momentum.length + 1 ||
				configuration.length != transferFunctions.length + 1) {
			System.out.println("Wrong parameters for setting MLP, check up the sizes of initialization data!");
			return;
		}

		//uncomment to get random MLP
		MLP mlp = new MLP(configuration, seed);
		
		//uncomment to get MLP from the weights.dat
		//MLP mlp = new MLP(configuration, "weights.dat");
		
		mlp.printWeights();
		mlp.getInputsFromFile(inputFilename);
		mlp.readTeacherOutput(inputFilename);
		
		
		mlp.runWithOutput(outputFilename);
		
		Scanner in = new Scanner(System.in);
		System.out.println("Enter type of learning (batch or single)");
		if(in.nextLine().equals("batch")) {
			int epochsCount;
			while(true){
				System.out.println("Enter number of epochs");
				epochsCount = in.nextInt();
				if (epochsCount > 0){
					break;
				}
			}
			for(int i=0; i<epochsCount; i++){
				mlp.shuffleInputData();
				for(double error : mlp.batchLearning()) {
					mlp.errorCurvePoints.add(error);
				}
			}	
		}
		else {
			int indexPattern = 0;
			for(double[] input : mlp.inputDataEpoch) {
				mlp.errorCurvePoints.add(mlp.singleStepLearning(input, indexPattern));
				indexPattern ++;
			}
		}
		
//		put errors to file for gnuplot
		mlp.printOutErrorCurve();
	}
}
