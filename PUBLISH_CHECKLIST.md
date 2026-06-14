# Google Play Publishing Checklist for Blob Volley

## Current Progress

- [x] Configure release signing in build.gradle.kts
- [x] Create ProGuard rules
- [x] Create .gitignore
- [x] Parameterize sensitive fields
- [x] Create environment sourcing script
- [x] Prepare store assets (icon-512.png, screenshots)
- [ ] Generate signing keystore
- [ ] Configure signing credentials
- [ ] Build release bundle
- [ ] Create Google Play Developer account
- [ ] Create privacy policy
- [ ] Submit app for review

---

## Step 1: Generate Signing Key

Run this command in the project root:

```bash
keytool -genkey -v -keystore blobvolley-release.keystore -alias blobvolley -keyalg RSA -keysize 2048 -validity 10000
```

You'll be prompted for:
- Keystore password (remember this!)
- Your name, organization, city, country (can skip with Enter)
- Key password (can be same as keystore password)

**CRITICAL**: Back up `blobvolley-release.keystore` securely. If lost, you can NEVER update your app.

---

## Step 2: Configure Signing Credentials

### Option A: Using environment variables (recommended)

```bash
cp env.sh.example env.sh
```

Edit `env.sh` with your passwords:
```bash
export RELEASE_STORE_FILE="../blobvolley-release.keystore"
export RELEASE_STORE_PASSWORD="your_actual_password"
export RELEASE_KEY_ALIAS="blobvolley"
export RELEASE_KEY_PASSWORD="your_actual_password"
```

Before building, source it:
```bash
source env.sh
```

### Option B: Using local.properties

Edit `local.properties` and uncomment/fill in:
```properties
RELEASE_STORE_FILE=../blobvolley-release.keystore
RELEASE_STORE_PASSWORD=your_actual_password
RELEASE_KEY_ALIAS=blobvolley
RELEASE_KEY_PASSWORD=your_actual_password
```

---

## Step 3: Build Release Bundle

```bash
source env.sh  # if using Option A
./gradlew bundleRelease
```

Output file: `app/build/outputs/bundle/release/app-release.aab`

---

## Step 4: Create Google Play Developer Account

1. Go to https://play.google.com/console
2. Sign in with your Google account
3. Pay the $25 one-time registration fee
4. Complete account details

---

## Step 5: Create Privacy Policy

Even simple games need a privacy policy. Options:

### Option A: GitHub Gist (free, quick)
1. Go to https://gist.github.com
2. Create a new gist with this content:

```
Privacy Policy for Blob Volley

Last updated: [DATE]

Blob Volley is a volleyball game with local multiplayer support.

Data Collection:
This app does not collect, store, or transmit any personal data.

Network Permissions:
The app uses network permissions (INTERNET, ACCESS_NETWORK_STATE,
ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE) solely for local
LAN multiplayer functionality. No data is sent to external servers.

Contact:
[YOUR EMAIL]
```

3. Save as public gist
4. Use the raw URL for Google Play

### Option B: Free generators
- https://www.freeprivacypolicy.com
- https://www.privacypolicygenerator.info

---

## Step 6: Create App on Google Play Console

1. Click "Create app"
2. Fill in:
   - App name: **Blob Volley**
   - Default language: English
   - App or game: **Game**
   - Free or paid: Your choice
3. Accept declarations

---

## Step 7: Complete Store Listing

Go to **Grow > Store presence > Main store listing**

### Required fields:
| Field | Value |
|-------|-------|
| App name | Blob Volley |
| Short description | Classic blob volleyball game with local multiplayer (max 80 chars) |
| Full description | Write 300-4000 characters about your game |
| App icon | Upload `store-assets/icon-512.png` |
| Feature graphic | 1024x500 PNG (optional but recommended) |
| Phone screenshots | Upload `store-assets/screen1.png`, `screen2.png` (min 2) |

### Example full description:
```
Blob Volley brings the classic blob volleyball experience to your Android device!

Features:
• Single player mode against AI
• Local multiplayer over WiFi/LAN
• Simple touch controls
• Nostalgic gameplay

Challenge your friends to a match over your local network, or practice your skills against the computer. Simple to learn, fun to master!
```

---

## Step 8: Complete App Content

Go to **Policy > App content** and complete:

### 8.1 Privacy Policy
- Paste your privacy policy URL

### 8.2 Ads
- Select "No, my app does not contain ads"

### 8.3 App access
- Select "All functionality is available without special access"

### 8.4 Content ratings
- Complete the IARC questionnaire
- Answer honestly (no violence, no gambling, etc.)
- Expected ratings: PEGI 3, ESRB Everyone

### 8.5 Target audience
- Select "18 and over" (simplest option, avoids kids' policy requirements)
- OR if targeting all ages, ensure compliance with family policies

### 8.6 News apps
- Select "No"

### 8.7 Data safety
Fill out the form:
- Data collected: None
- Data shared: None
- Security practices: Data encrypted in transit (for LAN multiplayer)

---

## Step 9: Set Up Releases

Go to **Release > Production**

1. Click "Create new release"
2. Let Google manage signing (recommended) OR upload your key
3. Upload `app-release.aab`
4. Add release notes:
   ```
   Initial release of Blob Volley!
   • Single player vs AI
   • Local multiplayer over LAN
   ```
5. Save

---

## Step 10: Select Countries & Pricing

Go to **Monetize > Countries/regions**
- Select where to publish (or "All countries")

Go to **Monetize > Pricing**
- Confirm free or set price

---

## Step 11: Final Review & Submit

1. Go to **Publishing overview**
2. Check all sections are complete (green checkmarks)
3. Click "Send for review"

Review typically takes 1-7 days for new apps.

---

## After Publishing

### Updating Your App

1. Increment version in `app/build.gradle.kts`:
   ```kotlin
   versionCode = 2  // increment this
   versionName = "1.1"  // update this
   ```

2. Build new bundle:
   ```bash
   source env.sh
   ./gradlew bundleRelease
   ```

3. In Play Console: Production > Create new release > Upload new `.aab`

### Monitoring

- Check **Statistics** for download numbers
- Check **Ratings and reviews** for user feedback
- Check **Android vitals** for crashes/ANRs

---

## Troubleshooting

### Build fails with signing error
- Check env.sh values are correct
- Ensure keystore file exists at specified path
- Verify passwords are correct

### Upload rejected
- Check minimum SDK (must be 26+)
- Ensure target SDK is recent (35)
- Check for 64-bit support

### App rejected in review
- Read rejection email carefully
- Common issues: missing privacy policy, incorrect content rating
- Fix issues and resubmit

---

## Important Files

| File | Purpose | Git |
|------|---------|-----|
| `blobvolley-release.keystore` | Signing key | **NEVER commit** |
| `env.sh` | Credentials | **NEVER commit** |
| `local.properties` | Local config | **NEVER commit** |
| `env.sh.example` | Template | Safe to commit |
| `store-assets/` | Store graphics | Safe to commit |