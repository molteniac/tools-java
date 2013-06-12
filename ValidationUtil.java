import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * バリデーションチェックのためのメソッド群．
 *
 * 漢字や記号のうち，Shift_JISとして扱えない文字を検証します．
 * また，ハイフン／ダッシュ系の記号とチルダを置換します．
 *
 */
public class ValidationUtil {
	
	protected static final Logger devLogger = Logger.getLogger("develop_log");

	private static final int ALLOW_EXCEPT_TILDE = 1;
	private static final int ALLOW_ALL = 2;
	
	/**
	 * 許可しないハイフン／ダッシュ系文字列．
	 * 
	 * 「‒」 U+2012 フィギアダッシュ 
	 * 「–」 U+2013 ENダッシュ 数値で使用する 
	 */
	private static char[] NG_DASHS = {'\u2012','\u2013'};
	
	/**
	 * 変換先のハイフン／ダッシュ．
	 *
	 * 「—」 U+2014 EM DASH/全角ダッシュ
	 */
	private static char TO_DASH ='\u2014';
	
	/**
	 * 許可するハイフン／ダッシュ系文字列．
	 * 
	 * 「-」 U+2010 HYPHEN-MINUS
	 * 「‐」 U+002D HYPHEN
	 * 「—」 U+2014 EM DASH/全角ダッシュ
	 * 「─」 U+2500  罫線横
	 * 「－」 U+FF0D FULLWIDTH HYPHEN-MINUS ×Shift_JIS
	 * 「−」 U+2212 MINUS SIGN
	 */
	private static char[] PERMIT_BARS = {'\u002D','\u2010','\u2014','\u2500','\uFF0D','\u2212'};
	
	/** 
	 * 許可しないチルダ．
	 *
	 * 「〜」 U+301C WAVE DASH/波ダッシュ
	 * 「~」 U+007E TILDE
	 * 「˜」 U+02DC SMALL TILDA 
	 * 「∼」 U+223C TILDE OPERATOR
	 */
	private static char[] NG_TILDAS = {'\u301c','\u007E','\u02DC','\u223C'};
	
	/**
	 * 許可するチルダ．
	 * 変換先としても使用．
	 *
	 * 「～」 U+FF5E 全角チルダ 
	 */
	private static char FULLWIDTH_TILDA = '\uFF5E';
	
	/**
	 * 許可するドット系記号．
	 *
	 * 中点
	 * 中点（半角）
	 */
	private static char[] PERMIT_DOTS = {'\u30FB','\uFF65'};

	/** 
	 * 記号系の禁則文字列パターン．
	 *
	 * oracleDiarect では U+301C(WAVE DASH) を U+FF5E(FULL WIDTH TILDA)に変換される．
	 * oracle 以外を考慮して，U+301C(WAVE DASH) を禁則文字として扱う．
	 * 
	 * 「~」 U+007E TILDE
	 * 「 ―」  U+2015 HORIZONTAL BAR
	 */
	private static final Pattern DENY_PATTERN = Pattern.compile("[^<>\"&,*$%|∥£€=`#~^\\[\\]();:{}\\u301c＾―]*");
	
	private static boolean isPermitExceptTilde(char c) {
		// BAR
		for (char sign : PERMIT_BARS) {
			if (sign == c) {
				return true;
			}
		}

		// DOT 
		for (char sign : PERMIT_DOTS) {
			if (sign == c) {
				return true;
			}
		}

		return false;
	}
	
	private static boolean isPermitAllInputs(char c) {
		// TILDA
		if (FULLWIDTH_TILDA == c){
			return true;
		}

		// BAR
		for (char sign : PERMIT_BARS) {
			if (sign == c) {
				return true;
			}
		}

		// DOT 
		for (char sign : PERMIT_DOTS) {
			if (sign == c){
				return true;
			}
		}

		return false;
	}
	
	/**
	 * ダッシュとチルダを置換する．
	 * 
	 */
	public static String replaceDashTilda(String str) {
		if (str == null) {
			return null;
		}

		StringBuffer sb = new StringBuffer();

		for (int i=0; i<str.length(); i++) {
			String s = String.valueOf(str.charAt(i));
			char c = str.charAt(i);
			
			// DASH 
			for (char sign : NG_DASHS) {
				if (sign == c) {
					s = String.valueOf(TO_DASH);
				}
			}
			// TILDA 
			for (char sign : NG_TILDAS) {
				if (sign == c) {
					s = String.valueOf(FULLWIDTH_TILDA);
				}
			}
            sb.append(s);
		}

		return sb.toString();
	}
	
	/**
	 * 記号系の禁則文字が含まれるかを検証する．
	 *
	 */
	public static boolean isForbidden(String arg) {
		Matcher m = DENY_PATTERN.matcher(arg);
		return !m.matches();
	}
	
	/**
	 * 機種依存文字でない全角記号文字の有無を検証する．
	 *
	 * バイトコードの参照URL: http://charset.7jp.net/sjis.html
	 * 
	 * Shift_JIS 変換不可文字が混入する文字列だった場合，true を返す．
	 * 以下で構成された文字列だった場合，false を返す．
	 *  - 許可された記号
	 *  - 半角数字、英字、カタカナ、記号
	 *  - 1byte文字
	 *  - 全角ひらがな、カタカナ、全角数字、全角英字
	 *  - Shift_JIS変換可能漢字
	 *  
	 *  @param str 検証対象文字列
	 *  @param checkType チェックオプションのフラグ
	 *      0: TILDA,BAR,DOT を許可しない
	 *      1: BAR,DOT を許可
	 *      2: TILDA,BAR,DOT を許可
	 */
	public static boolean isSymbolsForbidden(String str, int checkType) {
		
		boolean ret = false;
		byte[] bt = null;
		String s = null;
		char c;

		for (int i = 0; i < str.length(); i++) {
			try {
				s = str.substring(i, i+1);
				c = s.charAt(0);
			    bt = s.getBytes("Shift_JIS");
			    
			} catch (UnsupportedEncodingException e) {
			    e.printStackTrace();
			    return true;
			}
			
		    byte firstByte = bt[0];
		    String sFirst = Integer.toHexString(firstByte & 0xff);
		    
			// チェックオプション 1
			if (checkType == ALLOW_EXCEPT_TILDE && isPermitExceptTilde(c)) {
				devLogger.debug("[" + s + "]: ALLOW_EXCEPT_TILDE");
				
			// チェックオプション 2
			} else if (checkType == ALLOW_ALL && isPermitAllInputs(c)) {
				devLogger.debug("[" + s + "]: ALLOW_ALL");
			
		    // Shift_JISに変換できない文字列
		    } else if (firstByte == 63 && !s.equals("?")){
		        devLogger.debug("[" + s + "]: Shift_JISに変換できない文字");
			    ret = true;
			    
		    } else if ( sFirst.compareToIgnoreCase("0") >= 0 && sFirst.compareToIgnoreCase("127") < 0 
		           || sFirst.compareToIgnoreCase("161") >= 0 && sFirst.compareToIgnoreCase("223") < 0) {
		        devLogger.debug("[" + s + "]: 半角数字、英字、カタカナ、記号");
		    
			// 1byteチェック
		    }else if (bt.length == 1){
		    	devLogger.debug("[" + s + "]: 1byte文字");
	
		    // 2byte文字列チェック
		    } else {
		        byte secondByte = bt[1];
		        String doubleByteStr = sFirst.concat(Integer.toHexString(secondByte & 0xff));
		        
		        if (doubleByteStr.compareToIgnoreCase("8158") == 0  // 々
		         || doubleByteStr.compareToIgnoreCase("815b") >= 0 && doubleByteStr.compareToIgnoreCase("815d") <= 0 // ー ― ‐
		         || doubleByteStr.compareToIgnoreCase("824f") >= 0 && doubleByteStr.compareToIgnoreCase("8258") <= 0
		         || doubleByteStr.compareToIgnoreCase("8260") >= 0 && doubleByteStr.compareToIgnoreCase("8279") <= 0
		         || doubleByteStr.compareToIgnoreCase("8281") >= 0 && doubleByteStr.compareToIgnoreCase("829a") <= 0
		         || doubleByteStr.compareToIgnoreCase("829f") >= 0 && doubleByteStr.compareToIgnoreCase("82f1") <= 0
		         || doubleByteStr.compareToIgnoreCase("8340") >= 0 && doubleByteStr.compareToIgnoreCase("8396") <= 0) {
		        	devLogger.debug("[" +  s + "]: 全角ひらがな、カタカナ、全角数字、全角英字");
		        	
		        } else if (doubleByteStr.compareToIgnoreCase("889f") >= 0 && doubleByteStr.compareToIgnoreCase("9872") <= 0
		              || doubleByteStr.compareToIgnoreCase("989f") >= 0 && doubleByteStr.compareToIgnoreCase("9ffc") <= 0
		              || doubleByteStr.compareToIgnoreCase("e040") >= 0 && doubleByteStr.compareToIgnoreCase("eaa4") <= 0) {
		        	devLogger.debug("[" + s + "]: Permit.");
		        	
		        } else {
					devLogger.debug("[" + s + "]: Forbidden!");
		            ret = true;
		            break;
		        }
		    }
		}

		return ret;
	}
	
	/**
	 * Shift_JISへ変換できない漢字(旧字など)の有無を検証する．
	 * 
	 * 変換不可文字が混入する文字列だった場合，true を返す．
	 * 以下で構成された文字列だった場合，false を返す．
	 *  - 許可された記号
	 *  - 半角数字、英字、カタカナ、記号
	 *  - 1byte文字
	 *  - 全角ひらがな、カタカナ、全角数字、全角英字
	 *  - Shift_JIS変換可能漢字
	 *  
	 *  @param str 検証対象文字列
	 *  @param checkType チェックオプションのフラグ
	 *      0: TILDA,BAR,DOTを許可しない
	 *      1: BAR,DOTを許可
	 *      2: TILDA,BAR,DOTを許可
	 */
	public static boolean isKanjiForbidden(String str, int checkType) {
		
		boolean ret = false;
		byte[] bt = null;
		String s = null;
		char c;

		for (int i = 0; i < str.length(); i++) {
			try {
				s = str.substring(i, i+1);
				c = s.charAt(0);
			    bt = s.getBytes("Shift_JIS");
			    
			} catch (UnsupportedEncodingException e) {
			    e.printStackTrace();
			    return true;
			}
			
		    byte firstByte = bt[0];
		    String sFirst = Integer.toHexString(firstByte & 0xff);
		    
			// チェックオプション 1
			if (checkType == ALLOW_EXCEPT_TILDE && isPermitExceptTilde(c)) {
				devLogger.debug("[" + s + "]: ALLOW_EXCEPT_TILDE");

			// チェックオプション 2
			} else if (checkType == ALLOW_ALL && isPermitAllInputs(c)) {
				devLogger.debug("[" + s + "]: ALLOW_ALL");

		    // Shift_JISに変換できない文字列
		    } else if (firstByte == 63 && !s.equals("?")){
		        devLogger.debug("[" + s + "]: Shift_JISに変換できない文字");
			    ret = true;

		    } else if ( sFirst.compareToIgnoreCase("0") >= 0 && sFirst.compareToIgnoreCase("127") < 0 
		           || sFirst.compareToIgnoreCase("161") >= 0 && sFirst.compareToIgnoreCase("223") < 0) {
		        devLogger.debug("[" + s + "]: 半角数字、英字、カタカナ、記号");

			// 1byteチェック
		    } else if (bt.length == 1) {
		    	devLogger.debug("[" + s + "]: 1byte文字");

		    // 2byte文字列チェック
		    } else {
		        byte secondByte = bt[1];
		        String doubleByteStr = sFirst.concat(Integer.toHexString(secondByte & 0xff));

		        if (doubleByteStr.compareToIgnoreCase("8140") >= 0 && doubleByteStr.compareToIgnoreCase("81ac") <= 0
		         || doubleByteStr.compareToIgnoreCase("81b8") >= 0 && doubleByteStr.compareToIgnoreCase("81bf") <= 0
		         || doubleByteStr.compareToIgnoreCase("81c8") >= 0 && doubleByteStr.compareToIgnoreCase("81ce") <= 0
		         || doubleByteStr.compareToIgnoreCase("81da") >= 0 && doubleByteStr.compareToIgnoreCase("81e8") <= 0
		         || doubleByteStr.compareToIgnoreCase("81f0") >= 0 && doubleByteStr.compareToIgnoreCase("81f7") <= 0
		         || doubleByteStr.compareToIgnoreCase("81fc") >= 0 && doubleByteStr.compareToIgnoreCase("81fc") <= 0
		         || doubleByteStr.compareToIgnoreCase("824f") >= 0 && doubleByteStr.compareToIgnoreCase("8258") <= 0
		         || doubleByteStr.compareToIgnoreCase("8260") >= 0 && doubleByteStr.compareToIgnoreCase("8279") <= 0
		         || doubleByteStr.compareToIgnoreCase("8281") >= 0 && doubleByteStr.compareToIgnoreCase("829a") <= 0
		         || doubleByteStr.compareToIgnoreCase("829f") >= 0 && doubleByteStr.compareToIgnoreCase("82f1") <= 0
		         || doubleByteStr.compareToIgnoreCase("8340") >= 0 && doubleByteStr.compareToIgnoreCase("8396") <= 0
		         || doubleByteStr.compareToIgnoreCase("839f") >= 0 && doubleByteStr.compareToIgnoreCase("83b6") <= 0
		         || doubleByteStr.compareToIgnoreCase("83bf") >= 0 && doubleByteStr.compareToIgnoreCase("83d6") <= 0
		         || doubleByteStr.compareToIgnoreCase("8440") >= 0 && doubleByteStr.compareToIgnoreCase("8460") <= 0
		         || doubleByteStr.compareToIgnoreCase("8470") >= 0 && doubleByteStr.compareToIgnoreCase("8491") <= 0
		         || doubleByteStr.compareToIgnoreCase("849f") >= 0 && doubleByteStr.compareToIgnoreCase("84be") <= 0) {
		        	devLogger.debug("[" +  s + "]: 全角ひらがな、カタカナ、全角数字、全角英字、全角記号");
		        	
		        } else if (doubleByteStr.compareToIgnoreCase("889f") >= 0 && doubleByteStr.compareToIgnoreCase("9872") <= 0
		              || doubleByteStr.compareToIgnoreCase("989f") >= 0 && doubleByteStr.compareToIgnoreCase("9ffc") <= 0
		              || doubleByteStr.compareToIgnoreCase("e040") >= 0 && doubleByteStr.compareToIgnoreCase("eaa4") <= 0) {
		        	devLogger.debug("[" + s + "]: Permit.");
		        	
		        } else {
					devLogger.debug("[" + s + "]: Forbidden!");
		            ret = true;
		            break;
		        }
		    }
		}

		return ret;
	}
}
