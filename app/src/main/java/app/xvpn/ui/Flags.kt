package app.xvpn.ui

object Flags {
    private const val RI_FIRST = 0x1F1E6
    private const val RI_LAST = 0x1F1FF

    private val table = """
AF|Afghanistan
AL|Albania
DZ|Algeria
AD|Andorra
AO|Angola
AR|Argentina
AM|Armenia
AU|Australia
AT|Austria
AZ|Azerbaijan
BS|Bahamas
BH|Bahrain
BD|Bangladesh
BB|Barbados
BY|Belarus
BE|Belgium
BZ|Belize
BJ|Benin
BM|Bermuda
BT|Bhutan
BO|Bolivia
BA|Bosnia and Herzegovina|Bosnia
BW|Botswana
BR|Brazil
BN|Brunei
BG|Bulgaria
BF|Burkina Faso
KH|Cambodia
CM|Cameroon
CA|Canada
KY|Cayman Islands
CL|Chile
CN|China
CO|Colombia
CD|Democratic Republic of the Congo|DR Congo|Congo - Kinshasa|Congo Kinshasa|Congo
CR|Costa Rica
CI|Ivory Coast|Cote d'Ivoire|Côte d'Ivoire|Côte d’Ivoire|Cote d’Ivoire
HR|Croatia
CU|Cuba
CY|Cyprus
CZ|Czechia|Czech Republic|Czech
DK|Denmark
DO|Dominican Republic
EC|Ecuador
EG|Egypt
SV|El Salvador
EE|Estonia
ET|Ethiopia
FJ|Fiji
FI|Finland
FR|France
GE|Georgia
DE|Germany
GH|Ghana
GI|Gibraltar
GR|Greece
GL|Greenland
GT|Guatemala
GY|Guyana
HT|Haiti
HN|Honduras
HK|Hong Kong
HU|Hungary
IS|Iceland
IN|India
ID|Indonesia
IR|Iran
IQ|Iraq
IE|Ireland
IM|Isle of Man
IL|Israel
IT|Italy
JM|Jamaica
JP|Japan
JE|Jersey
JO|Jordan
KZ|Kazakhstan
KE|Kenya
KP|North Korea
KR|South Korea|Korea
KW|Kuwait
KG|Kyrgyzstan
LA|Laos
LV|Latvia
LB|Lebanon
LY|Libya
LI|Liechtenstein
LT|Lithuania
LU|Luxembourg
MO|Macau|Macao
MK|North Macedonia|Macedonia
MY|Malaysia
MV|Maldives
MT|Malta
MU|Mauritius
MX|Mexico
MD|Moldova
MC|Monaco
MN|Mongolia
ME|Montenegro
MA|Morocco
MZ|Mozambique
MM|Myanmar
NA|Namibia
NP|Nepal
NL|Netherlands|Holland
NZ|New Zealand
NI|Nicaragua
NG|Nigeria
NO|Norway
OM|Oman
PK|Pakistan
PS|Palestine
PA|Panama
PG|Papua New Guinea
PY|Paraguay
PE|Peru
PH|Philippines
PL|Poland
PT|Portugal
PR|Puerto Rico
QA|Qatar
RO|Romania
RU|Russia
RW|Rwanda
SM|San Marino
SA|Saudi Arabia
SN|Senegal
RS|Serbia
SC|Seychelles
SG|Singapore
SK|Slovakia
SI|Slovenia
ZA|South Africa
ES|Spain
LK|Sri Lanka
SD|Sudan
SR|Suriname
SE|Sweden
CH|Switzerland
SY|Syria
TW|Taiwan
TJ|Tajikistan
TZ|Tanzania
TH|Thailand
TT|Trinidad and Tobago
TN|Tunisia
TR|Turkey|Türkiye|Turkiye
TM|Turkmenistan
UG|Uganda
UA|Ukraine
AE|United Arab Emirates|UAE|Dubai
GB|United Kingdom|UK|England|Great Britain|Britain|Scotland|Wales
US|United States|USA|America
UY|Uruguay
UZ|Uzbekistan
VA|Vatican
VE|Venezuela
VN|Vietnam|Viet Nam
YE|Yemen
ZM|Zambia
ZW|Zimbabwe
ML|Mali
MG|Madagascar
GA|Gabon
NE|Niger
TG|Togo
SL|Sierra Leone
LR|Liberia
GN|Guinea
GW|Guinea-Bissau
GM|Gambia
MR|Mauritania
MW|Malawi
TD|Chad
SO|Somalia
SS|South Sudan
ER|Eritrea
BI|Burundi
CV|Cape Verde
DJ|Djibouti
LS|Lesotho
SZ|Eswatini|Swaziland
CF|Central African Republic
CG|Republic of Congo|Congo-Brazzaville|Congo Brazzaville
GQ|Equatorial Guinea
ST|Sao Tome and Principe
KM|Comoros
TL|Timor-Leste|East Timor
AG|Antigua and Barbuda
LC|Saint Lucia
KN|Saint Kitts and Nevis
VC|Saint Vincent
DM|Dominica
GD|Grenada
NR|Nauru
TV|Tuvalu
KI|Kiribati
MH|Marshall Islands
FM|Micronesia
PW|Palau
WS|Samoa
TO|Tonga
VU|Vanuatu
SB|Solomon Islands
FO|Faroe Islands
GG|Guernsey
CK|Cook Islands
RE|Reunion
GF|French Guiana
NU|Niue
AX|Aland
""".trimIndent()

    private val codes = HashSet<String>()
    private val matchers: List<Pair<Regex, String>>
    private val exact = HashMap<String, String>()

    init {
        val list = ArrayList<Pair<String, String>>()
        for (line in table.lines()) {
            val parts = line.split("|")
            if (parts.size < 2) continue
            codes += parts[0]
            for (n in parts.drop(1)) list += n.lowercase() to parts[0]
        }
        list.forEach { exact[it.first] = it.second }
        matchers = list.sortedByDescending { it.first.length }
            .map { Regex("(?<!\\p{L})" + Regex.escape(it.first) + "(?!\\p{L})") to it.second }
    }

    private val cache = HashMap<String, String>()

    private fun isRi(cp: Int) = cp in RI_FIRST..RI_LAST

    fun iso(name: String): String? = cache.getOrPut(name) {
        val clean = clean(name).lowercase()
        exact[clean]?.let { return@getOrPut it }
        matchers.firstOrNull { it.first.containsMatchIn(clean) }?.let { return@getOrPut it.second }

        Regex("^([A-Z]{2})(?![A-Za-z])").find(clean(name))?.groupValues?.get(1)
            ?.takeIf { it in codes }?.let { return@getOrPut it }
        ""
    }.ifEmpty { null }

    fun clean(name: String): String {
        val sb = StringBuilder()
        name.codePoints().forEach { if (!isRi(it)) sb.appendCodePoint(it) }
        val out = sb.toString().trim().trimStart('-', '|', '·', ':', ' ').trim()
        return out.ifEmpty { name }
    }
}
