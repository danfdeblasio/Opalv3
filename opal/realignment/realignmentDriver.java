package opal.realignment;

import facet.Facet;
import facet.FacetAlignment;
import opal.IO.Configuration;
import opal.makers.AlignmentMaker_SingleSequences;
import opal.IO.Inputs;

public class realignmentDriver {
	char[][] sequence;
	float[][][] structure_prob;
	Configuration[] configList;
	Configuration globalConfiguration;
	float wholeAlignmentScore;
	
	int[][] alignmentInstance;
	
	public realignmentDriver(char[][] se, float[][][] sp, Configuration[] cList, Configuration gConfig, float score) {
		sequence = se.clone();
		structure_prob = new float[se.length][se[0].length][3];
		wholeAlignmentScore = score;
		globalConfiguration = gConfig;
		
		for(int i=0;i<se.length;i++){
                int k=0;
                for(int j=0;j<se[0].length;j++){
                        if(sequence[i][j] == '-'){
                                structure_prob[i][j][0]=-1;
                                structure_prob[i][j][1]=-1;
                                structure_prob[i][j][2]=-1;
                        }
                        else{
                                structure_prob[i][j][0] = sp[i][k][0];
                                structure_prob[i][j][1] = sp[i][k][1];
                                structure_prob[i][j][2] = sp[i][k][2];
                                k++;
                        }
                }
        }
		
		configList = cList.clone();
		for(int i=0;i<configList.length;i++){
			configList[i].useLeftTerminal = false;
			configList[i].useRightTerminal = false;
		}
	}
	
	private float evaluateWindow(int startIndex, int endIndex){
		char[][] windowSequences = new char[sequence.length][endIndex-startIndex+1];
		float[][][] windowStructure = new float[sequence.length][][];
		for(int i=0; i<sequence.length; i++){
			int numberOfNonGaps = 0;
			for(int j=startIndex; j<=endIndex; j++){
				if(sequence[i][j] != '-') numberOfNonGaps++;
				windowSequences[i][j-startIndex] = sequence[i][j];
			}
			windowStructure[i] = new float[numberOfNonGaps][3];
			int k=0;
			for(int j=startIndex; j<=endIndex; j++){
				if(sequence[i][j] != '-'){
					windowStructure[i][k][0] = structure_prob[i][j][0];
					windowStructure[i][k][1] = structure_prob[i][j][1];
					windowStructure[i][k][2] = structure_prob[i][j][2];
					k++;
				}
			}
		}
		float value = Facet.defaultValue(new FacetAlignment(windowSequences, windowStructure));
		return (Float.isNaN(value))?0:value;
	}

	private char[][] realignWindow(int startIndex, int endIndex){
		
		// because the region may be larger than the windows we tested earlier
		float originalScore = evaluateWindow(startIndex, endIndex);
		
		
		char[][] originalWindowSequences = new char[sequence.length][endIndex-startIndex+1];
		String[] names = new String[sequence.length];
		int[] numberOfNonGaps = new int[sequence.length];
		int numberOfNonBlankSequences = 0;
		
		for(int i=0; i<sequence.length; i++){
			names[i] = Integer.toString(i);
			for(int j=startIndex; j<=endIndex; j++){
				if(sequence[i][j] != '-') numberOfNonGaps[i]++;
				originalWindowSequences[i][j-startIndex] = Character.toUpperCase(sequence[i][j]);
			}
			if(numberOfNonGaps[i] > 0) numberOfNonBlankSequences++;
		}
		
		char[][] windowSequences = new char[numberOfNonBlankSequences][];
		float[][][] windowStructure = new float[numberOfNonBlankSequences][][];
		
		int putInIndex = 0;
		for(int i=0; i<sequence.length; i++){
			int location = 0;
			if(numberOfNonGaps[i] > 0){
				windowSequences[putInIndex] = new char[numberOfNonGaps[i]];
				for(int j=startIndex; j<=endIndex; j++){
					if(sequence[i][j] != '-') windowSequences[putInIndex][location++] = Character.toUpperCase(sequence[i][j]);
				}
				windowStructure[putInIndex] = new float[numberOfNonGaps[i]][3];
				int k=0;
				for(int j=startIndex; j<=endIndex; j++){
					if(sequence[i][j] != '-'){
						windowStructure[putInIndex][k][0] = structure_prob[i][j][0];
						windowStructure[putInIndex][k][1] = structure_prob[i][j][1];
						windowStructure[putInIndex][k][2] = structure_prob[i][j][2];
						k++;
					}
				}
				putInIndex++;
			}
		}
		
		Inputs in = new Inputs();
		char[][] bestSubAlignment = null;
		float bestScore = originalScore;
		String bestConfig = "";
		
		for(int i=0; numberOfNonBlankSequences > 1 && i<configList.length; i++){
			
			configList[i].useLeftTerminal = (startIndex == 0);
			configList[i].useRightTerminal = (endIndex == sequence[0].length-1);
			if(globalConfiguration.realignment_use_terminals == Configuration.REALIGNMENT_TERMINALS.NEVER) 
				configList[i].useRightTerminal = configList[i].useLeftTerminal = false;
			if(globalConfiguration.realignment_use_terminals == Configuration.REALIGNMENT_TERMINALS.ALWAYS) 
				configList[i].useRightTerminal = configList[i].useLeftTerminal = true;
			
			//System.err.println("Window: [" + startIndex + "," + endIndex + "] " + configList[i].useLeftTerminal + " " + configList[i].useRightTerminal);
			
			AlignmentMaker_SingleSequences am = new AlignmentMaker_SingleSequences();
			am.initialize(configList[i].sc.convertSeqsToInts(windowSequences), names, configList[i], in);
			int[][] subAlignmentInstance = am.buildAlignment();
			//am.printOutput(subAlignmentInstance, null);
			float score = Facet.defaultValue(new FacetAlignment(configList[i].sc.convertIntsToSeqs(subAlignmentInstance),windowStructure));
			
			//globalConfiguration.realignmentLog += "Best Score:" + bestScore + "\tcurrent score:" + score + "\n";
			if(score>bestScore){
				bestScore = score;
				char[][] tempBestSubAlignment = configList[i].sc.convertIntsToSeqs(subAlignmentInstance);
				bestSubAlignment = new char[sequence.length][tempBestSubAlignment[0].length];
				putInIndex = 0;
				for(int j=0; j<sequence.length; j++){
					for(int k=0;k<tempBestSubAlignment[0].length;k++){
						if(numberOfNonGaps[j] > 0){
							bestSubAlignment[j][k] = tempBestSubAlignment[putInIndex][k];
						}else{
							bestSubAlignment[j][k] = '-';
						}
					}
					if(numberOfNonGaps[j] > 0) putInIndex++;
				}
				bestConfig = "used " + configList[i].toString() + " (" + numberOfNonBlankSequences + " of " + sequence.length + " sequences)";
			}
		}
		if(bestSubAlignment == null){
			bestSubAlignment = originalWindowSequences;
			bestConfig = "kept the same";
		}
		if(numberOfNonBlankSequences <= 1) bestConfig = "didn't try";
		globalConfiguration.realignmentLog += "Realigned window [" + startIndex + "," + endIndex + "] " + bestConfig + "\n";
		return bestSubAlignment;
	}
	
	public void simpleRealignment(int windowSize){
		float[] scores = new float[sequence[0].length-windowSize];
		float scoreTotal = 0;
		for(int i=0;i<sequence[0].length-windowSize;i++){
			scores[i] = evaluateWindow(i,i+windowSize);
			scoreTotal += scores[i];
		}
		
		String[] newAlignment = new String[sequence.length];
		for(int i=0;i<sequence.length;i++){
			newAlignment[i] = "";
		}
		
		// TODO threshold scores
		float threshold = 0;
		//(float)0.75 * wholeAlignmentScore;
		
		//threshold = (float)0.75 * (scoreTotal/scores.length);
		
		if(globalConfiguration.realignment_threshold_type == Configuration.THRESHOLD_TYPE.AVERAGE_WINDOW) 
			threshold = (float)globalConfiguration.realignment_threshold_value * (scoreTotal/scores.length);
		if(globalConfiguration.realignment_threshold_type == Configuration.THRESHOLD_TYPE.WHOLE_ALIGNMENT) 
			threshold = (float)globalConfiguration.realignment_threshold_value * wholeAlignmentScore;
		if(globalConfiguration.realignment_threshold_type == Configuration.THRESHOLD_TYPE.VALUE)
			threshold = globalConfiguration.realignment_threshold_value;
		
		System.err.println(scoreTotal + "/" + scores.length + " = " + (scoreTotal/scores.length) + "\t" + wholeAlignmentScore + "\t" 
				+ globalConfiguration.realignment_threshold_type + "," + globalConfiguration.realignment_threshold_value + "(" + threshold + ")\t" 
				+ globalConfiguration.realignment_window_type + "," + globalConfiguration.realignment_window_value + "\t" 
				+ globalConfiguration.realignment_minimum_type + "," + globalConfiguration.realignment_minimum_window_value
				);
		
		int[] countGood = new int[sequence[0].length];
		for(int i=0;i<sequence[0].length-windowSize;i++){
			if(scores[i]>threshold){
				for(int j=0;j<windowSize;j++){
					countGood[i+j]++;
				}
			}
		}
			
		// TODO merge low scoring windows
		int minimumBlockSize = 0;
		if(globalConfiguration.realignment_minimum_type == Configuration.WINDOW_SIZE_MINIMUM.VALUE)
			minimumBlockSize = (int)globalConfiguration.realignment_minimum_window_value;
		if(globalConfiguration.realignment_minimum_type == Configuration.WINDOW_SIZE_MINIMUM.WINDOW_MULTIPLIER)
			minimumBlockSize = (int)globalConfiguration.realignment_minimum_window_value * windowSize;
		
		//2*windowSize;
		boolean[] includeInRealignment = new boolean[sequence[0].length];
		for(int i=0;i<sequence[0].length;i++){
			includeInRealignment[i] = (countGood[i]<windowSize);
		}
		int[] blockStart = new int[sequence[0].length];
		int blockAssignI = 1;
		for(int i=1;i<sequence[0].length;i++){
			if(includeInRealignment[i]!=includeInRealignment[blockStart[blockAssignI-1]]){
				blockStart[blockAssignI] = i;
				blockAssignI++;
			}
		}
		//remove good blocks that are too short
		for(int i=0;i<blockAssignI-1;i++){
			if(blockStart[i+1] - blockStart[i] < minimumBlockSize && !includeInRealignment[blockStart[i]]){
				for(int j=blockStart[i];j<blockStart[i+1]; j++){
					includeInRealignment[j] = true;
				}
			}
		}
		
		//redo block definitions
		blockStart = new int[sequence[0].length];
		blockAssignI = 1;
		for(int i=1;i<sequence[0].length;i++){
			if(includeInRealignment[i]!=includeInRealignment[blockStart[blockAssignI-1]]){
				blockStart[blockAssignI] = i;
				blockAssignI++;
			}
		}
		//remove back blocks that are too short
		for(int i=0;i<blockAssignI-1;i++){
			if(blockStart[i+1] - blockStart[i] < minimumBlockSize && includeInRealignment[blockStart[i]]){
				for(int j=blockStart[i];j<blockStart[i+1]; j++){
					includeInRealignment[j] = false;
				}
			}
		}
		
		
		
		// TODO realign using each configuration
		int lastInAlignmentAlready = -1;
		for(int i=0;i<sequence[0].length;i++){
			if(!includeInRealignment[i]){
				if(lastInAlignmentAlready+1 < i){
					char[][] realignedRegion = realignWindow(lastInAlignmentAlready+1, i-1);
					for(int j=0; j<sequence.length; j++){
						for(int k=0; k<realignedRegion[j].length;k++)
						newAlignment[j] += realignedRegion[j][k];
					}
				}
				
				for(int j=0; j<sequence.length; j++){
					newAlignment[j] += sequence[j][i];
				}
				lastInAlignmentAlready = i;
			}
		}
		//if the alignment ends in a realignment region, process it;
		if(includeInRealignment[sequence[0].length-1]){
			char[][] realignedRegion = realignWindow(lastInAlignmentAlready+1, sequence[0].length-1);
			for(int j=0; j<sequence.length; j++){
				for(int k=0; k<realignedRegion[j].length;k++)
				newAlignment[j] += realignedRegion[j][k];
			}
		}
		
		// TODO merge new alignments
		char[][] newAlignmentChar = new char[newAlignment.length][];
		for(int j=0; j<sequence.length; j++){
			newAlignmentChar[j] = newAlignment[j].toCharArray();
		}
		alignmentInstance = globalConfiguration.sc.convertSeqsToInts(newAlignmentChar);
	}
	
	public int[][] newAlignment(){
		return alignmentInstance;
	}
}
