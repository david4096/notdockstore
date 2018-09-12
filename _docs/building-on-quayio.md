Now that you've perfected the `Dockerfile`, have built the image on your local host, have tested running the Docker container and tool packaged inside, and have released this version on GitHub, it's time to push the image to a place where others can use it.  For this you can use Docker Hub or GitLab but we prefer [Quay.io](http://quay.io) since it integrates really nicely with Dockstore.

You can manually `docker push` the image you have already built but the most reliable and transparent thing you can do is link your GitHub repository (and the Dockerfile contained within) to Quay.io.  This will cause Quay to automatically build the Docker image every time there is a change.

Log onto Quay.io now and setup a new repository (click the "+" icon).

![New Quay Repo](/assets/images/docs/quay_new_repo.png)

For your sanity, you should match the name to what you were using previously. So in this case, it's my username then the same repo name as in GitHub `denis-yuen/dockstore-tool-bamstats`. Also, Dockstore will only work with `Public` repositories currently. Notice I'm selecting "Link to a GitHub Repository Push", this is because we want Quay to automatically build our Docker image every time we update the repository on GitHub.  Very slick!

![Build Trigger](/assets/images/docs/build_all.png)

Click through to select the organization and repo that will act as the source for your image. Here I select the GitHub repo for `denis-yuen/dockstore-tool-bamstats` but this should be your username or organization in your tutorial run-through.

It will then ask if there are particular branches you want to build, I typically just let it build everything.

So every time you do a commit to your GitHub repo Quay automatically builds and tags a Docker image.  If this is overkill for you, consider setting up particular build trigger regular expressions at this step.

![Build Trigger](/assets/images/docs/run_trigger.png)

It will also ask you where your Dockerfile is located and where your build context is (normally the root).

At this point, you can confirm your settings and "Create Trigger" followed by "Run Trigger Now" to actually perform the initial build of the Docker images.  You'll need to click on the little gear icon next to your build trigger to accomplish this.

![Manual Trigger](/assets/images/docs/manual_trigger.png)

Manually trigger it with a version name of `1.25-6_1.1` for this tutorial. Normally, I let the build trigger build a new tag for each new release on GitHub. "latest" on Quay.io is built any time I check-in on any branch. This can be useful for development but is discouraged in favour of a tagged version number for formal releases of your tool.

In my example, I should see a `1.25-6_1.1` listed for this Quay.io Docker repository:

![Build Tags](/assets/images/docs/build_tags.png)

And I do, so this Docker image has been built successfully by Quay and is ready for sharing with the community.