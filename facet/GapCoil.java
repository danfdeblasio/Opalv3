package facet;

public class GapCoil {
	public static String Percentage(Configuration c){
		String rtn = "gapInCoilPercentage";
		if(c.structureType == Configuration.Structure.Probability) rtn += "-structureProbability";
		return rtn;
	}
	public static float Percentage(FacetAlignment a, Configuration c){
		float count_float = 0;
		int count = 0;
		int total = 0;
		for(int i=0;i<a.numberOfSequences;i++){
			for(int j=0;j<a.width;j++){
				if(a.sequence[i].charAt(j)=='-'){
					for(int k=0;k<a.numberOfSequences;k++){
						if(a.sequence[k].charAt(j) != '-'){
							total++;
							count += (a.structure[k].charAt(j)=='C')?1:0;
							count_float += a.structure_prob[k][j][0];
						}
					}
				}
			}
		}
		switch(c.structureType){
		case Probability: return (float) (1.0-(count_float/total));
		default: return (float) (1.0-((float)count/total));
		}
	}
}
