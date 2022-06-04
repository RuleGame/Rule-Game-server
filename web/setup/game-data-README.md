## Rule Game `game-data`

This Git repository serves as a place to store and track game-data folders for the Rule-Game server.

## Requirements

Have an instance of [Github Actions Server](https://github.com/kmui2/github-actions-server) running in Sapir or the server you want the `game-data` to exist in. See the [README](https://github.com/kmui2/github-actions-server/blob/master/README.md) for that repository.

## Editing folder contents

1. Make an edit to any of the folder contents (e.g. adding a new file, deleting an existing file, or editing an existing file)

2. Commit the changes (push to GitHub too if edited locally)

3. Wait for the upload to complete in the [GitHub Actions Tab](https://github.com/lupyanlab/Rule-Game-game-data/actions)

## Adding a New Folder

1. Add a new folder at the top level directory of this repository.
2. Add upload configuration in the config folder in Sapir `/var/www/rule-game/github-actions-server/config.json`

	Template (replace `[name]` with the name of the folder):

	```json
	"[name]": {
		"basePath": "/opt/tomcat/game-data"
	},
	```

3. Add the upload URL in the Rule Game game data [secrets page](https://github.com/lupyanlab/Rule-Game-game-data/settings/secrets/actions)
	
	Template (assuming the port of the [Github Actions Server](https://github.com/kmui2/github-actions-server) that is running is 7189):

	-`UPLOAD_[NAME]_URL`: https://sapir.psych.wisc.edu:7189/[name]/upload 

4. Modify the GitHub workflow [master.yml](./.github/workflows/master.yml) to add a zip step and an upload step:
	
	Template:
	
	```yml
	- name: Zip [name] folder
	  run: cd [name] && zip -r ../[name].zip *

	- name: Upload [name]
	  run: curl --fail -k -F "secret=${{ secrets.UPLOAD_SECRET }}" -F "build=@[name].zip" "${{ secrets.UPLOAD_[NAME]_URL }}"
	```

## Removing a Folder

1. Remove the folder at the top level directory of this repository.
2. Remove the upload configuration in the config folder in Sapir `/var/www/rule-game/github-actions-server/config.json`
3. Remove the upload URL in the Rule Game game data [secrets page](https://github.com/lupyanlab/Rule-Game-game-data/settings/secrets/actions)
4. Modify the GitHub workflow [master.yml](./.github/workflows/master.yml) to remove the zip step and the upload step.

## How it Works

Using [GitHub Actions](https://github.com/features/actions) and the custom [workflow](./github/workflows/master.yml) setup here, every change to the master branch in this repository will upload a new copy of all the folders included in the workflow file.

The running instance of [GitHub Actions Server](https://github.com/kmui2/github-actions-server) in Sapir uses a configuration file that contains the paths to where each folder will be uploaded to. For example, the `boards` folder has an upload path of `/opt/tomcat/game-data` which corresponds to folder `/opt/tomcat/game-data/boards` when uploaded.

Whenever a change is made in this repository, GitHub Actions will kick off a job that will run a zip and upload step. The zip step is to zip the folder that will exist only for that GitHub Actions job running, and the upload step will send a POST request to the GitHub Actions server with the zipped folder included. Then, the server will unzip the folder and overwrite the existing folder with the contents in the uploaded zipped folder.

The folders uploaded will overwrite the existing folders in Sapir (e.g uploading the `boards` folder will overwrite the existing `boards` folder `/opt/tomcat/game-data/boards` in Sapir). So, you should **not** make manual edits to the folder contents on the server to avoid changes being permanently lost, and all edits should be done on GitHub.
